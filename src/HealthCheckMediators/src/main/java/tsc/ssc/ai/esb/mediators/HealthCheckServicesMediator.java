package tsc.ssc.ai.esb.mediators;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMDocument;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMFactory;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axis2.Constants;
import org.apache.axis2.description.AxisService;
import org.apache.axis2.description.AxisServiceGroup;
import org.apache.http.protocol.HTTP;
import org.apache.synapse.MessageContext;
import org.apache.synapse.commons.json.JsonUtil;
import org.apache.synapse.config.SynapseConfigUtils;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.endpoints.Endpoint;
import org.apache.synapse.message.processor.MessageProcessor;

import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndContentImpl;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndEntryImpl;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.feed.synd.SyndFeedImpl;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedOutput;

/**
 * This mediator will loop through all of the services and check their status.
 * Then it will create an Atom feed.
 * 
 * @author sbechtel
 * 
 *         Code pulled together from:
 * @see https://github.com/wso2/wso2-synapse/blob/master/modules/core/src/main/
 *      java/org/apache/synapse/mediators/transform/PayloadFactoryMediator.java
 * @see https://rometools.github.io/rome/RssAndAtOMUtilitiEsROMEV0.
 *      5AndAboveTutorialsAndArticles/RssAndAtOMUtilitiEsROMEV0.
 *      5TutorialUsingROMEWithinAServletToCreateAndReturnAFeed.html
 * @see https://mvnrepository.com/artifact/com.rometools
 * @see http://javadox.com/com.rometools/rome/1.5.1/com/rometools/rome/feed/atom
 *      /package-summary.html
 * @see https://www.google.com/url?sa=t&rct=j&q=&esrc=s&source=web&cd=1&cad=rja&
 *      uact=8&ved=2ahUKEwiAhcGUxO_dAhVM3FMKHfBhCy0QFjAAegQICRAB&url=https%3A%2F
 *      %2Fsvn.wso2.org%2Frepos%2Fwso2%2Fcarbon%2Fplatform%2Ftrunk%2Fcomponents%
 *      2Fservice-mgt%2Forg.wso2.carbon.service.mgt%2Fsrc%2Fmain%2Fjava%2Forg%
 *      2Fwso2%2Fcarbon%2Fservice%2Fmgt%2FServiceAdmin.java&usg=
 *      AOvVaw2VVtFfGRNTk2uDThlupBPk
 * 
 */
public class HealthCheckServicesMediator extends org.apache.synapse.mediators.AbstractMediator {

	private static final String PROXY_SERVICE = "ProxyService";
	private static final String ENDPOINT = "Endpoint";
	private static final String SERVICE_STATUS_INACTIVE = "Inactive";
	private static final String SERVICE_STATUS_ACTIVE = "Active";
	private static final String MESSAGE_PROCESSOR = "MessageProcessor";
	private static final String TSC_SSC_AI = "tsc.ssc.ai";
	private static final String FEED_TYPE_ATOM_0_3 = "atom_0.3";
	private final static QName TEXT_ELEMENT = new QName("http://ws.apache.org/commons/ns/payload", "text");
	private final static String TEXT_CONTENT_TYPE = "text/plain";

	private static final DateFormat DATE_PARSER = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");

	public boolean mediate(MessageContext context) {
		// MessageContext is synapses context

		// ServerName
		String serverName;
		try {
			serverName = InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			serverName = (String) context.getProperty("SERVER_IP");
		}

		// RightNow
		String ts = new Timestamp(System.currentTimeMillis()).toString().substring(0, 19);

		// working variables
		List<SyndEntry> entries = new ArrayList<SyndEntry>();

		// Create entries
		createMessageProcessorStatuses(context, serverName, ts, entries);
		createEndpointStatuses(context, serverName, ts, entries);
		createProxyStatuses(context, serverName, ts, entries);

		// Stich it all into a feed
		SyndFeed feed = new SyndFeedImpl();
		feed.setFeedType(FEED_TYPE_ATOM_0_3);
		// available types: (rss_0.90, rss_0.91, rss_0.92, rss_0.93, rss_0.94,
		// rss_1.0 rss_2.0 or atom_0.3)");

		feed.setTitle("WSO2 Services Status Feed");
		feed.setDescription(serverName);
		feed.setAuthor(TSC_SSC_AI);
		feed.setEntries(entries);

		// Roll ups
		int totalServices = entries.size();
		int totalServicesInactive = getServicesInactiveCount(entries);
		int isAllServicesUp = totalServices == 0 ? 1 : new Double(Math.floor(totalServicesInactive/totalServices)).intValue();

		// Using WebMaster slot to indicate how many services there are.
		feed.setWebMaster(new Integer(totalServices).toString());

		// Using Copyright slot to indicate how many services are down.
		feed.setCopyright(new Integer(totalServicesInactive).toString());

		// Using docs as a overall boolean status of if the services are all up
		// or if at least one is down.
		// 0=something is down
		// 1=all are up
		feed.setDocs(new Integer(isAllServicesUp).toString());

		// Create the feed to be in a memory buffer so we can write it out as
		// text
		OutputStream feedOutput = new ByteArrayOutputStream();
		SyndFeedOutput output = new SyndFeedOutput();
		PrintWriter writer = new PrintWriter(feedOutput);
		try {
			output.output(feed, writer);
			feedOutput.flush();
			feedOutput.close();
			writer.flush();
			writer.close();
		} catch (IOException e) {
			log.error(e.getMessage());
			return false;
		} catch (FeedException e) {
			log.error(e.getMessage());
			return false;
		}

		// set the output in a way wso2 can service it
		org.apache.axis2.context.MessageContext axis2MessageContext = ((Axis2MessageContext) context)
				.getAxis2MessageContext();
		JsonUtil.removeJsonPayload(axis2MessageContext);
		axis2MessageContext.getEnvelope().getBody().addChild(getTextElement(feedOutput.toString()));

		axis2MessageContext.setProperty(Constants.Configuration.MESSAGE_TYPE, TEXT_CONTENT_TYPE);
		axis2MessageContext.setProperty(Constants.Configuration.CONTENT_TYPE, TEXT_CONTENT_TYPE);
		handleSpecialProperties(TEXT_CONTENT_TYPE, axis2MessageContext);

		axis2MessageContext.removeProperty("NO_ENTITY_BODY");

		return true;
	}

	private int getServicesInactiveCount(List<SyndEntry> entries) {
		int cntOfServicesDown = 0;
		for (SyndEntry entry : entries) {
			if (entry.getDescription().getValue().equals(SERVICE_STATUS_INACTIVE)) {
				cntOfServicesDown++;
			}
		}
		return cntOfServicesDown;
	}

	private OMElement getTextElement(String content) {
		OMFactory factory = OMAbstractFactory.getOMFactory();
		OMElement textElement = factory.createOMElement(TEXT_ELEMENT);
		if (content == null) {
			content = "";
		}
		textElement.setText(content);
		return textElement;
	}

	// This is copied from PropertyMediator, required to change Content-Type
	private void handleSpecialProperties(Object resultValue, org.apache.axis2.context.MessageContext axis2MessageCtx) {
		axis2MessageCtx.setProperty(org.apache.axis2.Constants.Configuration.CONTENT_TYPE, resultValue);
		Object o = axis2MessageCtx.getProperty(org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS);
		Map headers = (Map) o;
		if (headers != null) {
			headers.remove(HTTP.CONTENT_TYPE);
			headers.put(HTTP.CONTENT_TYPE, resultValue);
		}
	}

	private void createEndpointStatuses(MessageContext context, String serverName, String ts, List<SyndEntry> entries) {
		String serviceName;
		String serviceStatus;
		SyndEntry entry;
		SyndContent description;
		SyndContent titleEx;

		// Get endpoints results
		if (context != null) {
			if (context.getConfiguration() != null) {
				Map<String, Endpoint> eps = context.getConfiguration().getDefinedEndpoints();

				for (Map.Entry<String, Endpoint> service : eps.entrySet()) {
					if (service == null) {
						continue;
					}

					try {
						serviceName = service.getValue().getName();
						serviceStatus = service.getValue().readyToSend() ? SERVICE_STATUS_ACTIVE
								: SERVICE_STATUS_INACTIVE;

						entry = new SyndEntryImpl();
						entry.setPublishedDate(DATE_PARSER.parse(ts));
						entry.setAuthor(ENDPOINT);
						entry.setTitle(serviceName);

						description = new SyndContentImpl();
						description.setType("text/plain");
						description.setValue(serviceStatus);
						entry.setDescription(description);

						entries.add(entry);

					} catch (ParseException e) {
						log.error(e.getMessage());
					}
				}
			}
		}
	}

	private void createProxyStatuses(MessageContext context, String serverName, String ts, List<SyndEntry> entries) {
		String serviceName;
		String serviceStatus;
		SyndEntry entry;
		SyndContent description;
		SyndContent titleEx;

		// note context.getConfiguration().getProxyServices() doesn't provide
		// ProxyService objects with isActive.
		// So we went at it from another angle based on admin services code.

		// Get Proxy results
		if (context != null) {
			if (context.getConfiguration() != null) {
				HashMap<String, AxisService> services = context.getConfiguration().getAxisConfiguration().getServices();

				for (AxisService service : services.values()) {

					try {

						serviceName = service.getName();
						serviceStatus = service.isActive() ? SERVICE_STATUS_ACTIVE : SERVICE_STATUS_INACTIVE;
						entry = new SyndEntryImpl();
						entry.setPublishedDate(DATE_PARSER.parse(ts));
						entry.setAuthor(PROXY_SERVICE);
						entry.setTitle(serviceName);

						description = new SyndContentImpl();
						description.setType("text/plain");
						description.setValue(serviceStatus);
						entry.setDescription(description);

						entries.add(entry);
					} catch (ParseException e) {
						log.error(e.getMessage());
					} catch (Exception e) {
						log.error(e.getMessage());
					}
				}
			}
		}
	}

	private void createMessageProcessorStatuses(MessageContext context, String serverName, String ts,
			List<SyndEntry> entries) {
		String serviceName;
		String serviceStatus;
		SyndEntry entry;
		SyndContent description;
		SyndContent titleEx;

		// Get Message Processor results
		if (context != null) {
			if (context.getConfiguration() != null) {
				Map<String, MessageProcessor> mps = context.getConfiguration().getMessageProcessors();

				for (Map.Entry<String, MessageProcessor> service : mps.entrySet()) {
					if (service == null) {
						continue;
					}

					try {
						serviceName = service.getValue().getName();
						serviceStatus = service.getValue().isDeactivated() ? SERVICE_STATUS_INACTIVE
								: SERVICE_STATUS_ACTIVE;

						entry = new SyndEntryImpl();
						entry.setPublishedDate(DATE_PARSER.parse(ts));
						entry.setAuthor(MESSAGE_PROCESSOR);
						entry.setTitle(serviceName);

						description = new SyndContentImpl();
						description.setType("text/plain");
						description.setValue(serviceStatus);
						entry.setDescription(description);

						entries.add(entry);

					} catch (ParseException e) {
						log.error(e.getMessage());
					}
				}
			}
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	protected <T> T getInputFromContext(MessageContext context, Class... classes)
			throws XMLStreamException, JAXBException {
		T output;
		Writer writer = null;
		Reader reader = null;
		try {
			writer = new StringWriter();
			context.getEnvelope().getBody().getFirstElement().serialize(writer);
			reader = new StringReader(writer.toString());
			JAXBContext jaxbContext = JAXBContext.newInstance(classes);
			Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
			output = (T) unmarshaller.unmarshal(reader);
			return output;
		} finally {
			if (writer != null) {
				try {
					writer.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					log.error(e.getMessage());
				}
			}
		}
	}

	public String getMessage(MessageContext context) throws Exception {
		StringWriter writer = new StringWriter();
		context.getEnvelope().getBody().getFirstElement().serialize(writer);
		return writer.toString();
	}

	@SuppressWarnings("rawtypes")
	protected <T> SOAPEnvelope generateEnvelope(T envelopeContents, Class... classes) throws JAXBException {
		JAXBContext jaxbContext = JAXBContext.newInstance(classes);
		Marshaller marshaller = jaxbContext.createMarshaller();
		StringWriter writer = new StringWriter();
		marshaller.marshal(envelopeContents, writer);

		SOAPEnvelope envelope = OMAbstractFactory.getSOAP11Factory().getDefaultEnvelope();
		OMDocument omDoc = OMAbstractFactory.getSOAP11Factory().createOMDocument();
		omDoc.addChild(envelope);

		envelope.getBody().addChild(SynapseConfigUtils.stringToOM(writer.toString()));
		return envelope;
	}
}
