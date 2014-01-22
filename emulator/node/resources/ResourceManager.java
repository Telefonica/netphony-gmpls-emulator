package tid.emulator.node.resources;

import java.net.Inet4Address;
import java.util.Hashtable;

import tid.emulator.node.transport.lsp.LSP;
import tid.emulator.node.transport.lsp.LSPKey;
import tid.emulator.node.transport.lsp.te.LSPTE;
import tid.pce.pcep.messages.PCEPRequest;
import tid.pce.pcep.messages.PCEPResponse;
import tid.rsvp.messages.RSVPPathMessage;
import tid.rsvp.messages.RSVPPathTearMessage;
import tid.rsvp.messages.te.RSVPTEPathMessage;
import tid.rsvp.messages.te.RSVPTEResvMessage;


public interface ResourceManager {
		
	public boolean checkResources(LSPTE lsp);
	
	public boolean reserveResources(LSPTE lsp, Inet4Address dstNodeLink);
	
	
	public boolean freeResources(LSPTE lsp);
	
	public PCEPRequest getPCEPRequest(LSPTE lspInfo);
	
	//public boolean checkLocalResources(LSPTE lspInfo);
	
	public RSVPTEPathMessage getRSVPTEPathMessageFromPCEPResponse(LSPTE lsp);
	public RSVPPathTearMessage getRSVPPathTearMessage(LSPTE lsp);
	public RSVPTEResvMessage getRSVPResvMessageFromDestination(RSVPTEPathMessage path, LSPTE lsp);
	public RSVPTEPathMessage forwardRSVPpath(LSPTE lsp,RSVPTEPathMessage path);
	
	public Hashtable<LSPKey, Inet4Address> getProxHopIPv4List();

	public void setProxHopIPv4List(Hashtable<LSPKey, Inet4Address> proxHopIPv4List);

	public Hashtable<LSPKey, Inet4Address> getPreviousHopIPv4List();

	public void setPreviousHopIPv4List(Hashtable<LSPKey, Inet4Address> previousHopIPv4List);
}
