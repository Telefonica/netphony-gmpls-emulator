/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tid.emulator.node;

import java.util.logging.*;
import java.util.Properties;
import java.util.Vector;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

import tid.pce.client.*;
import tid.pce.client.emulator.AutomaticTesterStatistics;
import tid.pce.pcepsession.PCEPSessionsInformation;
import tid.pce.server.lspdb.ReportDB_Redis;
import tid.pce.tedb.DomainTEDB;
import tid.pce.tedb.InterDomainEdge;
import tid.pce.tedb.IntraDomainEdge;
import tid.pce.tedb.MDTEDB;
import tid.pce.tedb.SimpleTEDB;
import tid.pce.tedb.TEDB;
import tid.rsvp.RSVPProtocolViolationException;
import tid.rsvp.constructs.SenderDescriptor;
import tid.rsvp.messages.*;
import tid.rsvp.objects.*;
import tid.emulator.node.management.NodeManagementSever;
import tid.emulator.node.resources.ResourceManager;
import tid.emulator.node.resources.mpls.MPLSResourceManager;
import tid.emulator.node.resources.sson.SSONResourceManager;
import tid.emulator.node.resources.wson.WSONResourceManager;
import tid.emulator.node.tedb.SimpleLocalTEDB;
import tid.emulator.node.transport.PathComputationClient;
import tid.emulator.node.transport.defineLocalTEDB;
import tid.emulator.node.transport.ospf.OSPFController;
import tid.emulator.node.transport.rsvp.*;
import tid.emulator.node.transport.lsp.*;
import tid.emulator.node.transport.lsp.te.TechnologyParameters;

import org.jgrapht.graph.DirectedWeightedMultigraph;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import org.omg.CORBA.PolicyErrorCodeHelper;
import org.savarese.vserv.tcpip.*;

import com.savarese.rocksaw.net.RawSocket;

import static com.savarese.rocksaw.net.RawSocket.PF_INET;
import static com.savarese.rocksaw.net.RawSocket.PF_INET6;
import static com.savarese.rocksaw.net.RawSocket.getProtocolByName;
import static org.savarese.vserv.tcpip.ICMPPacket.OFFSET_ICMP_CHECKSUM;

/**
 * This class represent a Reconfigurable Optical Add Drop Multiplexer Node. A ROADM is composed
 * by different interfaces to interconnect itself with other ROADMs. It also is composed by 
 * different clients that come from IP nodes transport requirements. Internal interconnections
 * such as client-interface(channel) and interface(channel) - interface(channel) are stored
 * in the connections vector.
 * 
 * As can be seen in the ROADM architecture scheme, FIXME:(Nombre archivo), dividing the ROADM
 * in sub-blocks, there are 5 with different functionalities that are listed below:
 * 
 *   1.- TED: 			The complete resources that the ROADM has, joint with its utilization, which means
 *   					the complete LSP reservation implications.
 *   2.- PCEPClient:	The PCEPClient is in charge of communicate the ROADM with the domain PCE used to
 *   					calculate the optimum path between two nodes.
 *   3.- RSVPManager:	Block that takes care of listening RSVP messages from the network, maintenance of
 *   					RSVP sessions (LSP implications) and translating LSP petitions into the adequate 
 *   					RSVP message.
 *   4.- LSPManager:	The LSP Manager role is getting all the LSP that are born in the ROADM and the ones
 *   					which it is one of the middle nodes, in order to be capable of taking actions to
 *   					restore connectivity in case of failure or path re-optimization.
 *   5.- UserInterface:	Configuration & Operation module that will rule the ROADM behaviour.
 * 
 * @author fmn
 */

public class NetworkNode {

	//Properties of the Node (IP & technology)
	/**
	 * Class with the spcecific information of the Node
	 */
	private NodeInformation nodeInformation;
    
    //Components of the node (TEDB, RSVPManager, LSPManager, PathComputationClient, OSPFController, ResourceManager)
    
    /**
     * Domain TEDB: specific for each technology
     */
    private DomainTEDB ted;
    /**
     * Multi Domain TEDB: specific for each technology in case we have interDomain Links
     */
    private TEDB MDted;
    /**
     * Generic RSVP Manager
     */
    private RSVPManager rsvpManager;
    /**
     * Generic LSP Manager
     */
    private LSPManager managerLSP;
    /**
     * Module encharged of the PCEP Session 
     */
    private PathComputationClient PCC;
    /**
     * Controller for the OSPF Session messages with the PCE
     */
    private OSPFController ospfController;
    /**
     * Specific resourceManager for each technology
     */
    private ResourceManager resourceManager;
    /**
     * Management Module for the manual node configuration
     */
    private NodeManagementSever nodeManagement;
    
    public static Logger log;
    
     /**
      * Launches a fast PCEP Session Server to initiate 
      * and tear down LSPs remotely.
      */
     private FastPCEPSessionServer fastPCEPSessionServer;
    
     private RemoteLSPInitPCEPSessionServer rlsserver;
     
     private boolean isStateful = true;
 	private boolean statefulDFlag =true;
 	private boolean statefulTFlag = true;
 	private boolean statefulSFlag = true;     
     
     
     private boolean isActive = true;
     
     private boolean isSRCapable = true;
     private int MSD = 47;
     
    /**
     *
     * Default constructor. Initializes all attributes
     *
     */
     
    public NetworkNode(){
    	
    	// Create information of te Node
    	nodeInformation = new NodeInformation();
    	nodeInformation.readNodeConfiguration();
    	
    	// Create the Logs
    	log = Logger.getLogger("ROADM");
		log.setLevel(Level.ALL);
		Logger log2 = Logger.getLogger("PCCClient");
		log2.setLevel(Level.ALL);
		Logger log3 = Logger.getLogger("OSPFParser");
		log3.setLevel(Level.ALL);
		Logger log4 = Logger.getLogger("PCEPParser");
		log4.setLevel(Level.SEVERE);
		
		try{
			FileHandler fh = new FileHandler("Roadm.log", false);
			log.addHandler(fh);
			FileHandler fh2 = new FileHandler("PCCClient.log", false);
			log2.addHandler(fh2);
			FileHandler fh3 = new FileHandler("OSPFParser.log", false);
			log3.addHandler(fh3);
			FileHandler fh4 = new FileHandler("PCEPParser.log", false);
			log4.addHandler(fh4);
			
			log.setLevel(Level.ALL);
			log2.setLevel(Level.ALL);
			log3.setLevel(Level.ALL);
			
			if (nodeInformation.isSetTraces() == false){
				log.info("Traces Out!");
				log.setLevel(Level.SEVERE);
				log2.setLevel(Level.SEVERE);
				log3.setLevel(Level.SEVERE);
			}else {
				log.setLevel(Level.ALL);
				log2.setLevel(Level.ALL);
				log3.setLevel(Level.ALL);
			}

		}catch(IOException e){
			log.warning("Exception with logs!");
			e.printStackTrace();
			System.exit(1);
		}
		
		log.severe("ROADM Created");
		log.info("ROADM created con info");
    	if (nodeInformation.isRsvpMode()== true){
	        // Create the RSVP Manager
	        rsvpManager = new RSVPManager();
    	}
    	log3.info("Log de OSPF Creado!");
        // Creamos el LSP Manager
    	managerLSP = new LSPManager(isStateful);
		
        //Create the PathComputationClient
        PCC = new PathComputationClient();
        
        //The Traffic Engineering Database
		ted=new SimpleLocalTEDB();
		if (nodeInformation.getNodeTechnology()==TechnologyParameters.SSON){
			((SimpleLocalTEDB)ted).initializeFromFile(nodeInformation.getTopologyName(), null, false, 0, Integer.MAX_VALUE, true , false);
		}else
			((SimpleLocalTEDB)ted).initializeFromFile(nodeInformation.getTopologyName(), null, false, 0, Integer.MAX_VALUE, false , false);
	
			// Create the Multi Domain TEDB
			MDted = new MDTEDB();
			// Initialice
			((MDTEDB)MDted).initializeFromFileInterDomainLinks(nodeInformation.getTopologyName());


			
		//TEDB CREADA --> recorrer grafo y podar
		SimpleDirectedWeightedGraph<Object, IntraDomainEdge> LocalGraph = defineLocalTEDB.podateGraph(((SimpleTEDB)ted).getNetworkGraph(), nodeInformation.getId());
		((SimpleLocalTEDB)ted).setNetworkGraph(LocalGraph);
		
		

			log.info("Is Multi-Domain, create the MDTEDB!");
			//MDTEDB CREADA --> recorrer grafo y podar
			DirectedWeightedMultigraph<Object, InterDomainEdge> MDLocalGraph = defineLocalTEDB.podateMDGraph(((MDTEDB)MDted).getNetworkDomainGraph(), nodeInformation.getId());
			((MDTEDB)MDted).setNetworkDomainGraph(MDLocalGraph);
		
		//Creamos el Resource Manager (con MDted Siempre)
			if (nodeInformation.getNodeTechnology() == TechnologyParameters.MPLS){
	    		resourceManager = new MPLSResourceManager((SimpleLocalTEDB)ted, nodeInformation.getId(), (MDTEDB)MDted);
	        }else if (nodeInformation.getNodeTechnology() == TechnologyParameters.WSON){
	        	resourceManager = new WSONResourceManager((SimpleLocalTEDB)ted, nodeInformation.getId(), (MDTEDB)MDted);
	        }else if (nodeInformation.getNodeTechnology() == TechnologyParameters.SSON){
	        	resourceManager = new SSONResourceManager((SimpleLocalTEDB)ted, nodeInformation.getId(), (MDTEDB)MDted);
	        }else if (nodeInformation.getNodeTechnology() == TechnologyParameters.UNKNOWN){
	        	log.severe("Technology not valid!");
	        	System.exit(-1);
	        }
    	
    	//OSPF
		ospfController = new OSPFController();
				    	
    	// Configure all the Modules
    	if (nodeInformation.isRsvpMode()== true){
    		rsvpManager.configureRSVPManager(nodeInformation.getId(), resourceManager, managerLSP);
    		managerLSP.configureLSPManager(rsvpManager, nodeInformation.getId(), PCC, resourceManager, nodeInformation.isRsvpMode());
    	}else{
    		managerLSP.configureLSPManager(null, nodeInformation.getId(), PCC, resourceManager, nodeInformation.isRsvpMode());
    	}
    	ospfController.configureOSPFController(nodeInformation.getId(), ted);
    	//Fast PCEP Session for remote invokation
		fastPCEPSessionServer = new FastPCEPSessionServer(this.getManagerLSP(), nodeInformation.getId(), nodeInformation.getNodeTechnology());
		
		//Automatic PCCNode Session
		nodeInformation.setStatefull(false);
		rlsserver = new RemoteLSPInitPCEPSessionServer(managerLSP, nodeInformation.getId(), nodeInformation.getNodeTechnology(), nodeInformation.isStatefull());
    }
    
    public void startNode() {
		log.info("Checking database...");
		ReportDB_Redis rptdb = new ReportDB_Redis(nodeInformation.getId().toString(),"10.95.161.138");
		rptdb.fillFromDB();
		managerLSP.setDataBaseVersion(rptdb.getVersion());
		managerLSP.setRptdb(rptdb);
		log.info("added new rptdb to this node");
    	
    	//Añadimos el PCE y creamos la sesión PCEP
    	//PCC.addPCE(false,nodeInformation.getPceID(),nodeInformation.getPcePort(), isStateful, isActive, managerLSP,isSRCapable,MSD);
    	PCC.addPCE(false,nodeInformation.getPceID(),nodeInformation.getPcePort(), 
    			isStateful, isActive, statefulDFlag, statefulTFlag, statefulSFlag, managerLSP,
    			isSRCapable,MSD);
		//Start the RSVP Manager
		if (nodeInformation.isRsvpMode()== true)
			rsvpManager.startRSVPManager();
    	
    	//Start node Management
    	nodeManagement = new NodeManagementSever(this);		
		nodeManagement.start();
		
		//Start OSPF Controller
		ospfController.initialize();
		
		//Start the Fast PCEP Session for remote invocation
		AutomaticTesterStatistics stats = new AutomaticTesterStatistics(0);
		fastPCEPSessionServer.setStats(stats);
		fastPCEPSessionServer.start();
		
		//Start the Automatic PCCNode Session
		Thread sessionServer = new Thread(rlsserver);
		sessionServer.start();
	}
 
    public LSPManager getManagerLSP() {
		return managerLSP;
	}

	public void setManagerLSP(LSPManager managerLSP) {
		this.managerLSP = managerLSP;
	}

	public DomainTEDB getTed() {
		return ted;
	}

	public void setTed(DomainTEDB ted) {
		this.ted = ted;
	}
	
	public TEDB getMDted() {
		return MDted;
	}

	public void setMDted(TEDB mDted) {
		MDted = mDted;
	}

	public NodeInformation getNodeInformation() {
		return nodeInformation;
	}

	public void setNodeInformation(NodeInformation nodeInformation) {
		this.nodeInformation = nodeInformation;
	}

	public PathComputationClient getPCC() {
		return PCC;
	}

	public void setPCC(PathComputationClient pCC) {
		PCC = pCC;
	}
	
	
	public boolean isStatefulDFlag() {
		return statefulDFlag;
	}

	public void setStatefulDFlag(boolean statefulDFlag) {
		this.statefulDFlag = statefulDFlag;
	}

	public boolean isStatefulTFlag() {
		return statefulTFlag;
	}

	public void setStatefulTFlag(boolean statefulTFlag) {
		this.statefulTFlag = statefulTFlag;
	}

	public boolean isStatefulSFlag() {
		return statefulSFlag;
	}

	public void setStatefulSFlag(boolean statefulSFlag) {
		this.statefulSFlag = statefulSFlag;
	}	
	
	
	
}