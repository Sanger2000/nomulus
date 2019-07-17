package google.registry.monitoring.blackbox.messages;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Strings.isNullOrEmpty;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import google.registry.monitoring.blackbox.exceptions.EppClientException;
import google.registry.monitoring.blackbox.exceptions.ResponseException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class EppMessage {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static String TEMPLATE_BASE_PATH = "google/registry/monitoring/blackbox/xml/";
  public static int HEADER_LENGTH = 4;
  private final static DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();

  private static final XPath xpath;
  private static final Schema eppSchema;

  protected Document message;

  // These are the xpath expressions for EPP success status codes and failure status codes.
  // commands can send one or the other to implement XPASS or XFAIL testing.
  @VisibleForTesting
  static final String XPASS_EXPRESSION =
      String.format("//eppns:result[@code>='%s'][@code<'%s']", 1000, 2000);

  @VisibleForTesting
  static final String XFAIL_EXPRESSION =
      String.format("//eppns:result[@code>='%s'][@code<'%s']", 2000, 3000);

  // "Security" errors from RFC 5730, plus the error we get when we end
  // up no longer logged (see b/28196510).
  // 2002    "Command use error"
  // 2200    "Authentication error"
  // 2201    "Authorization error"
  // 2202    "Invalid authorization information"
  @VisibleForTesting
  static final String AUTHENTICATION_ERROR =
      "//eppns:epp/eppns:response/eppns:result[@code!='2002' and @code!='2200' "
          + "and @code!='2201' and @code!='2202']";

  // As per RFC 1035 section 2.3.4 http://tools.ietf.org/html/rfc1035#page-10 and updated by
  // http://tools.ietf.org/html/rfc1123#page-13 which suggests a domain part length
  // of 255 SHOULD be supported, so we are permitting something close to that, but reserve
  // at least two characters for a TLD and a full stop (".") character.
  static final int MAX_DOMAIN_PART_LENGTH = 252;
  static final int MAX_SLD_DOMAIN_LABEL_LENGTH = 255;
  static final String VALID_DOMAIN_PART_REGEX;
  static final String VALID_TLD_PART_REGEX;
  static final String VALID_SLD_LABEL_REGEX;
  static {
    // tld label part may contain a dot and end with a dot, and must
    // start and end with 0-9 or a-zA-Z but may contain any number of
    // dashes (minus signs "-") in between (even consecutive)
    VALID_TLD_PART_REGEX = String.format(
        "\\p{Alnum}[-\\p{Alnum}]{0,%s}\\.{0,1}[-\\p{Alnum}]{0,%s}\\p{Alnum}",
        (EppRequestMessage.MAX_DOMAIN_PART_LENGTH - 2), (EppRequestMessage.MAX_DOMAIN_PART_LENGTH - 2));
    // domain label part ("left" of the tld) must start and end with 0-9 or a-zA-Z but
    // may contain any number of dashes (minus signs "-") in between (even consecutive)
    VALID_DOMAIN_PART_REGEX = String.format("\\p{Alnum}[-\\p{Alnum}]{0,%s}\\p{Alnum}",
        (EppRequestMessage.MAX_DOMAIN_PART_LENGTH - 2));
    VALID_SLD_LABEL_REGEX = String.format("(?=.{4,%s})%s\\.%s(\\.)*",
        EppRequestMessage.MAX_SLD_DOMAIN_LABEL_LENGTH,
        EppRequestMessage.VALID_DOMAIN_PART_REGEX,
        EppRequestMessage.VALID_TLD_PART_REGEX);

    xpath = XPathFactory.newInstance().newXPath();
    xpath.setNamespaceContext(new EppNamespaceContext());
    docBuilderFactory.setNamespaceAware(true);

    String path = "com/google/domain/registry/monitoring/blackbox/xml/xsd/";
    StreamSource[] sources;
    try {
      sources = new StreamSource[] {
          new StreamSource(EppRequestMessage.class.getClassLoader().getResourceAsStream(path + "eppcom.xsd")),
          new StreamSource(EppRequestMessage.class.getClassLoader().getResourceAsStream(path + "epp.xsd")),
          new StreamSource(EppRequestMessage.class.getClassLoader().getResourceAsStream(path + "host.xsd")),
          new StreamSource(
              EppRequestMessage.class.getClassLoader().getResourceAsStream(path + "contact.xsd")),
          new StreamSource(EppRequestMessage.class.getClassLoader().getResourceAsStream(path + "domain.xsd")),
          new StreamSource(EppRequestMessage.class.getClassLoader().getResourceAsStream(path + "rgp.xsd")),
          new StreamSource(EppRequestMessage.class.getClassLoader().getResourceAsStream(path + "mark.xsd")),
          new StreamSource(EppRequestMessage.class.getClassLoader().getResourceAsStream(path + "dsig.xsd")),
          new StreamSource(EppRequestMessage.class.getClassLoader().getResourceAsStream(path + "smd.xsd")),
          new StreamSource(EppRequestMessage.class.getClassLoader().getResourceAsStream(path + "launch.xsd")),
      };
      SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
      eppSchema = schemaFactory.newSchema(sources);
    } catch (SAXException e) {
      throw new ExceptionInInitializerError(e);
    }
  }
  /**
   * Validate an EPP XML document against the set of XSD files that make up the EPP XML Schema.
   *
   * <p>Note that the document must have the namespace attributes set where expected (i.e. must be
   * built using a DocumentBuilder with namespace awareness set if using a DocumentBuilder).
   *
   * @param xml an XML Document to validate
   * @throws SAXException if the document is not valid
   */
  public static void eppValidate(Document xml) throws SAXException, IOException {
    Validator schemaValidator = eppSchema.newValidator();
    schemaValidator.validate(new DOMSource(xml));
  }

  /**
   * Implements the {@link NamespaceContext} interface and adds an EPP namespace URI (prefix eppns).
   */
  static class EppNamespaceContext implements NamespaceContext {

    final Map<String, String> nsPrefixMap = new HashMap<>();
    final Map<String, Set<String>> nsUriMap = new HashMap<>();

    public EppNamespaceContext() {
      try {
        addNamespace(XMLConstants.XML_NS_PREFIX, XMLConstants.XML_NS_URI);
        addNamespace(XMLConstants.XMLNS_ATTRIBUTE, XMLConstants.XMLNS_ATTRIBUTE_NS_URI);
        addNamespace("eppns", "urn:ietf:params:xml:ns:epp-1.0");
        addNamespace("contactns", "urn:ietf:params:xml:ns:contact-1.0");
        addNamespace("domainns", "urn:ietf:params:xml:ns:domain-1.0");
        addNamespace("hostns", "urn:ietf:params:xml:ns:host-1.0");
        addNamespace("launchns", "urn:ietf:params:xml:ns:launch-1.0");
      } catch (Exception e) {
        // this should never happen here but if it does, we just turn it into a runtime exception
        throw new IllegalArgumentException(e);
      }

    }

    void addNamespace(String prefix, String namespaceURI) throws Exception {
      checkArgument(!isNullOrEmpty(prefix), "prefix");
      checkArgument(!isNullOrEmpty(namespaceURI), "namespaceURI");
      if (nsPrefixMap.containsKey(prefix)) {
        throw new RuntimeException("key already exists: " + prefix);
      }
      nsPrefixMap.put(prefix, namespaceURI);
      if (nsUriMap.containsKey(namespaceURI)) {
        nsUriMap.get(namespaceURI).add(prefix);
      } else {
        Set<String> prefixSet = new HashSet<>();
        prefixSet.add(prefix);
        nsUriMap.put(namespaceURI, prefixSet);
      }
    }
    @Override
    public String getNamespaceURI(String prefix) {
      checkArgument(!isNullOrEmpty(prefix), "prefix");
      return nsPrefixMap.getOrDefault(prefix, XMLConstants.NULL_NS_URI);
    }

    @Override
    public String getPrefix(String namespaceURI) {
      checkArgument(!isNullOrEmpty(namespaceURI), "namespaceURI");
      return getPrefixes(namespaceURI).next();
    }

    @Override
    public Iterator<String> getPrefixes(String namespaceURI) {
      checkArgument(!isNullOrEmpty(namespaceURI), "namespaceURI");
      if (nsUriMap.containsKey(namespaceURI)) {
        return nsUriMap.get(namespaceURI).iterator();
      } else {
        return Collections.emptyIterator();
      }
    }
  }

  /**
   * Verify an XML Document as an EPP Response using the provided XPath expressions.
   *
   * <p>This will first validate the document against the EPP schema, then run through the list
   * of xpath expressions -- so those need only look for specific EPP elements + values.
   *
   * @param xml the XML Document containing the EPP reponse to verify
   * @param expressions a list of XPath expressions to query the document with.
   * @param validate a boolean flag to control if schema validation occurs (useful for testing)
   *
   * @throws IOException if InputStream throws one
   * @throws EppClientException if the EPP response cannot be read, parsed, or doesn't containing
   *     matching data specified in expressions
   */
  protected static void verifyEppResponse(Document xml, List<String> expressions, boolean validate)
      throws ResponseException {
    if (validate) {
      try {
        eppValidate(xml);
      } catch (SAXException | IOException e) {
        throw new ResponseException(e);
      }
    }

    try {
      for (String exp : expressions) {
        NodeList nodes = (NodeList) xpath.evaluate(exp, xml, XPathConstants.NODESET);
        if (nodes.getLength() == 0) {
          throw new ResponseException("invalid EPP response. failed expression " + exp);
        }
      }
    } catch (XPathExpressionException e) {
      throw new ResponseException(e);
    }
  }

  /**
   * A helper method to extract a value from an element in an XML document.
   *
   * @return the text value for the element, or null is the element is not found
   */
  @Nullable
  public static String getElementValue(Document xml, String expression) {
    try {
      return (String) xpath.evaluate(expression, xml, XPathConstants.STRING);
    } catch (XPathExpressionException e) {
      logger.atSevere().withCause(e).log("Bad expression: %s", expression);
      return null;
    }
  }

  /**
   * A helper method to transform an XML Document to a string.
   * - e.g. a returned string might look like the following for a Document with a root element
   * of "foo" that has a child element of "bar" which has text of "baz":<br>
   * {@code '<foo><bar>baz</bar></foo>'}
   *
   * @param xml the Document to transform
   * @return the resulting string or {@code null} if {@code xml} is {@code null}.
   * @throws EppClientException if the transform fails
   */
  @Nullable
  public static String xmlDocToString(@Nullable Document xml) throws EppClientException {
    if (xml == null) {
      return null;
    }
    try {
      Transformer transformer = TransformerFactory.newInstance().newTransformer();
      StreamResult result = new StreamResult(new StringWriter());
      DOMSource source = new DOMSource(xml);
      transformer.transform(source, result);
      return result.getWriter().toString();
    } catch (TransformerException e) {
      throw new EppClientException(e);
    }
  }


  /**
   * A helper method to transform an XML Document to a byte array
   * using the XML Encoding when converting from a String (see
   * xmlDocToString).
   *
   * @param xml the Document to transform
   * @return the resulting byte array.
   * @throws EppClientException if the transform fails
   */
  protected static byte[] xmlDocToByteArray(Document xml) throws EppClientException {
    try {
      Transformer transformer = TransformerFactory.newInstance().newTransformer();
      StreamResult result = new StreamResult(new StringWriter());
      DOMSource source = new DOMSource(xml);
      transformer.transform(source, result);
      String resultString = result.getWriter().toString();
      if (isNullOrEmpty(resultString)) {
        throw new EppClientException("unknown error converting Document to intermediate string");
      }
      String encoding = xml.getXmlEncoding();
      // this is actually not a problem since we can just use the default
      if (encoding == null) {
        encoding = Charset.defaultCharset().name();
      }
      return resultString.getBytes(encoding);
    } catch (TransformerException | UnsupportedEncodingException e) {
      throw new EppClientException(e);
    }
  }
  /**
   * A helper method to transform an byte array to an XML
   * {@link Document} using {@code docBuilderFactory}
   *
   * @param responseBuffer the byte array to transform
   * @return the resulting Document
   * @throws EppClientException if the transform fails
   */
  protected static Document byteArrayToXmlDoc(byte[] responseBuffer)
      throws ResponseException {
    Document xml;
    try {
      DocumentBuilder builder = docBuilderFactory.newDocumentBuilder();
      ByteArrayInputStream byteStream = new ByteArrayInputStream(responseBuffer);
      xml = builder.parse(byteStream);
    } catch (ParserConfigurationException | SAXException | IOException e) {
      throw new ResponseException(e);
    }
    return xml;

  }

  /**
   * Reads one of a set of EPP templates (included as resources in our
   * jar) and finds nodes using an xpath expression, then replaces the
   * node value of the first child, returning the transformed XML as a
   * Document.
   *
   * <p>E.g. to replace the value "@@CLTRID@@" in the {@code <clTRID>}
   * node with a client transaction ID, use the mapping
   * {@code <"//domainns:clTRID", "AAA-123-BBBB">} (or whatever the ID is).
   *
   * @param template the relative (unqualified) name of the template file to use
   * @param replacements a map of strings to replace in the template
   *     keyed by the xpath expression to use to find the nodes to operate
   *     on with the value being the text to use as the replacement
   * @return the transformed EPP document
   * @throws IOException if the template cannot be read
   * @throws EppClientException if there are issues parsing the
   *     template or evaluating the xpath expression, or if the resulting
   *     document is not valid EPP
   * @throws IllegalArgumentException if the xpath expression query
   *     yields anything other than an Element node type
   */
  public static Document getEppDocFromTemplate(String template, Map<String, String> replacements)
      throws IOException, EppClientException {
    Document xmlDoc;
    try (InputStream is = EppMessage.class.getClassLoader().getResourceAsStream(TEMPLATE_BASE_PATH + template)) {
      DocumentBuilder builder = docBuilderFactory.newDocumentBuilder();
      xmlDoc = builder.parse(is);
      for (String key : replacements.keySet()) {
        NodeList nodes = (NodeList) xpath.evaluate(key, xmlDoc, XPathConstants.NODESET);
        for (int i = 0; i < nodes.getLength(); i++) {
          Node node = nodes.item(i);
          if (node.getNodeType() != Node.ELEMENT_NODE) {
            throw new IllegalArgumentException(
                String.format("xpath expression (%s) must result in Element nodes, got %s",
                    key, node.getNodeType()));
          }
          node.getFirstChild().setNodeValue(replacements.get(key));
        }
      }
      eppValidate(xmlDoc);
    } catch (SAXException | XPathExpressionException | ParserConfigurationException e) {
      throw new EppClientException(e);
    }
    return xmlDoc;
  }

}
