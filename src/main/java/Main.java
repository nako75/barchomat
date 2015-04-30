import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sir.barchable.clash.VillageAnalyzer;
import sir.barchable.clash.model.LogicParser;
import sir.barchable.clash.protocol.MessageReader;
import sir.barchable.clash.protocol.Pdu;
import sir.barchable.clash.protocol.ProtocolTool;
import sir.barchable.clash.protocol.TypeFactory;
import sir.barchable.clash.proxy.Connection;
import sir.barchable.clash.proxy.MessageTapFilter;
import sir.barchable.clash.proxy.ProxySession;
import sir.barchable.util.Hex;

import java.io.*;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static sir.barchable.util.BitBucket.NOWHERE;

/**
 * Decrypt a session
 *
 * @author Sir Barchable
 */
public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    @Parameter(names = {"-d", "--definition-dir"}, description = "Directory to load the protocol definition from")
    private File resourceDir;

    @Parameter(names = {"-l", "--logic"}, description = "Directory/file to load the game logic from")
    private File logicFile;

    @Parameter(names = {"-w", "--working-dir"}, description = "Directory to read streams from")
    private File workingDir = new File(".");

    @Parameter(names = {"-h", "--hex"}, description = "Dump messages as hex")
    private boolean dumpHex;

    @Parameter(names = {"-j", "--json"}, description = "Dump messages as json")
    private boolean dumpJson;

    public static void main(String[] args) throws IOException {
        Main main = new Main();
        JCommander commander = new JCommander(main);
        try {
            commander.parse(args);
            main.run();
        } catch (ParameterException e) {
            commander.usage();
        } catch (Exception e) {
            log.error("", e);
            System.err.println("Oops: " + e.getMessage());
        }
    }

    private TypeFactory typeFactory;
    private MessageReader messageReader;
    private ObjectWriter jsonWriter = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET)
        .writer();

    private void run() throws IOException, InterruptedException {
        if (!workingDir.exists()) {
            throw new FileNotFoundException(workingDir.toString());
        }

        if (resourceDir != null) {
            if (!resourceDir.exists()) {
                throw new FileNotFoundException(resourceDir.toString());
            }
            typeFactory = new TypeFactory(ProtocolTool.read(resourceDir));
        } else {
            typeFactory = new TypeFactory();
        }

        if (logicFile == null) {
            File[] apks = new File(".").listFiles((dir, name) -> name.endsWith(".apk"));
            if (apks.length != 1) {
                throw new FileNotFoundException("Logic file not specified");
            } else {
                logicFile = apks[0];
            }
        }

        messageReader = new MessageReader(typeFactory);

        File clientDumpFile = new File(workingDir, "client.txt");
        File serverDumpFile = new File(workingDir, "server.txt");
        try (
            // Client output stream
            OutputStreamWriter clientOut = new OutputStreamWriter(new FileOutputStream(clientDumpFile), UTF_8);
            // Server output stream
            OutputStreamWriter serverOut = new OutputStreamWriter(new FileOutputStream(serverDumpFile), UTF_8)
        ) {
            dumpSession(clientOut, serverOut);
        }
    }

    /**
     * Decrypt and dump a session. Expects two files in the working directory, <i>client.stream</i> and
     * <i>server.stream</i>, containing raw tcp stream captures from a clash session.
     * <p>
     * Capture: <code>tcpflow port 9339</code>
     * @param clientOut where to write the decoded client stream
     * @param serverOut where to write the decoded server stream
     */
    public void dumpSession(Writer clientOut, Writer serverOut) throws IOException, InterruptedException {
        Dumper clientDumper = new Dumper(Pdu.Type.Client, clientOut);
        Dumper serverDumper = new Dumper(Pdu.Type.Server, serverOut);
        File clientFile = new File(workingDir, "client.stream");
        File serverFile = new File(workingDir, "server.stream");
        MessageTapFilter tapFilter = new MessageTapFilter(
            messageReader,
            new VillageAnalyzer(LogicParser.loadLogic(logicFile))
        );
        try (
            // Client connection
            Connection clientConnection = new Connection("Client", new FileInputStream(clientFile), NOWHERE);
            // Server connection
            Connection serverConnection = new Connection("Server", new FileInputStream(serverFile), NOWHERE)
        ) {
            ProxySession session = ProxySession.newSession(clientConnection, serverConnection, clientDumper::dump, serverDumper::dump, tapFilter);
            VillageAnalyzer.logSession(session);
        }
    }

    /**
     * Formats and writes PDUs to an output stream.
     */
    class Dumper {
        private final Pdu.Type type;
        private Writer out;

        /**
         * Construct a dumper.
         *
         * @param type the type of Pdu to dump
         * @param out the stream to write to.
         */
        public Dumper(Pdu.Type type, Writer out) {
            this.type = type;
            this.out = out;
        }

        synchronized public Pdu dump(Pdu pdu) throws IOException {
            if (pdu.getType() == type) {
                if (dumpHex) {
                    dumpHex(pdu);
                }
                if (dumpJson) {
                    dumpJson(pdu);
                }
                out.flush();
            }
            return pdu;
        }

        void dumpJson(Pdu pdu) throws IOException {
            Map<String, Object> message = messageReader.readMessage(pdu);
            if (message != null) {
                String name = typeFactory.getMessageNameForId(pdu.getId()).get();
                out.write('"' + name + "\": ");
                jsonWriter.writeValue(out, message);
                out.write('\n');
            }
        }

        private void dumpHex(Pdu pdu) throws IOException {
            out.write(pdu.toString() + "\n");
            Hex.dump(pdu.getPayload(), out);
        }
    }
}
