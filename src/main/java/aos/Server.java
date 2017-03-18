package aos;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import helpers.ConfigurationLoader;
import helpers.Linker;
import helpers.MAPConfigurationLoader;
import helpers.ProcessFactory;
import helpers.RKey;
import helpers.Registry;
import helpers.Repository;
import snapshot.RecvCamera;
import snapshot.SnapshotThread;
/**
 * 
 * @author Zeqing Li, zxl165030, The University of Texas at Dallas
 *
 */
public class Server {    
    /**
     * @param args
     *            args[0] - port, args[1] - node id, args[2] - file
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            System.err.println("Usage: java Server <port> <node id> <config file>");
            System.exit(1);
        }

        int port = Integer.parseInt(args[0]);
        int myId = Integer.parseInt(args[1]);
        String relativePath = args[2];
        
        /* Load configuration file */
        Repository registry = new Registry();   // Manage system variables              
        ConfigurationLoader configLoader = 
                new MAPConfigurationLoader(registry);
        
        // load List<Node> neighbors
        // load GlobalParams;
        configLoader.loadConfig(relativePath, myId);
        
        try {
            
            // 1. Setup registry.
            @SuppressWarnings("unchecked")
            List<Node> neighbors = (List<Node>) registry.getObject(RKey.KEY_NEIGHBORS.name());
            Linker linker = new Linker(myId, neighbors);
            linker.buildChannels(port);
            registry.addLinker(linker);
            ProcessFactory factory = registry.getProcessFactory();
            
            
            // 2. Produce main thread process
            RecvCamera proc = factory.createCamera();
            
            /* Use thread pools to manage process behaviors */
            ExecutorService executorService = Executors.newFixedThreadPool(50);
            for(Node node : linker.getNeighbors()){
                Runnable task = new ListenerThread(myId, node.getNodeId(), proc);
                executorService.execute(task);
            }
            linker.multicast(linker.getNeighbors(), Tag.VECTOR, "Test");
            
            // 3. Wait for spanning tree setup
            proc.waitForDone();  
            
            // 4. Setup Chandy Lamport Protocol
            SnapshotThread chandyLamportProtocol = new SnapshotThread(myId, proc);
            executorService.execute(chandyLamportProtocol);
            Thread.sleep(5000);
           
            // 5. Setup MAP protocol
            MAPThread mapProtocol = new MAPThread(myId, proc);
            executorService.execute(mapProtocol);
            
            Thread.sleep(30000);
            linker.close();
            executorService.shutdown();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        
    }
    


}
