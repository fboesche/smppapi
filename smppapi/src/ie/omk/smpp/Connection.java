/*
 * Java SMPP API
 * Copyright (C) 1998 - 2002 by Oran Kelly
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 * 
 * A copy of the LGPL can be viewed at http://www.gnu.org/copyleft/lesser.html
 * Java SMPP API author: orank@users.sf.net
 * Java SMPP API Homepage: http://smppapi.sourceforge.net/
 * $Id$
 */
package ie.omk.smpp;

import java.io.*;
import java.net.*;
import java.util.*;

import ie.omk.smpp.event.ConnectionObserver;
import ie.omk.smpp.event.SMPPEvent;
import ie.omk.smpp.event.ReceiverExceptionEvent;
import ie.omk.smpp.event.ReceiverExitEvent;
import ie.omk.smpp.event.ReceiverStartEvent;

import ie.omk.smpp.message.*;
import ie.omk.smpp.net.*;
import ie.omk.smpp.util.*;
import ie.omk.debug.Debug;

/** SMPP client connection (ESME).
  * @author Oran Kelly
  * @version 1.0
  */
public class Connection
    implements java.lang.Runnable
{
    /** SMPP Transmitter connection type. */
    public static final int	TRANSMITTER = 1;

    /** SMPP Receiver connection type. */
    public static final int	RECEIVER = 2;

    /** SMPP Transciever connection type. */
    public static final int	TRANSCEIVER = 3;

    /** Connection state: not bound to the SMSC. */
    public static final int	UNBOUND = 0;

    /** Connection state: waiting for successful acknowledgement to bind
      * request.
      */
    public static final int	BINDING = 1;

    /** Connection state: bound to the SMSC. */
    public static final int	BOUND = 2;

    /** Connection state: waiting for successful acknowledgement to unbind
      * request or waiting for application to respond to unbind request.
      */
    public static final int	UNBINDING = 3;

    /** Type of this SMPP connection. */
    private int			connectionType = 0;

    /** Packet listener thread for Asyncronous comms. */
    private Thread		rcvThread = null;

    /** The first ConnectionObserver added to this object. */
    private ConnectionObserver	singleObserver = null;

    /** The list of ConnectionObservers on this object. This list does
     * <b>NOT</b> include the first observer referenced by
     * <code>singleObserver</code>.
     */
    private ArrayList		observers = null;

    /** Byte buffer used in readNextPacketInternal. */
    private byte[]		buf = new byte[300];

    /** Sequence numbering scheme to use for this connection. */
    private SequenceNumberScheme seqNumScheme = new DefaultSequenceScheme();

    /** The network link (virtual circuit) to the SMSC */
    private SmscLink		link = null;

    /** SMPP protocol version number.
     */
    protected SMPPVersion	interfaceVersion = SMPPVersion.V33;

    /** Current state of the SMPP connection.
      * Possible states are UNBOUND, BINDING, BOUND and UNBINDING.
      */
    private int			state = UNBOUND;

    /** Specify whether the listener thread will automatically ack
      * enquire_link primitives received from the Smsc
      */
    protected boolean		ackQryLinks = true;

    /** Automatically acknowledge incoming deliver_sm messages.
      * Only valid for the Receiver
      */
    protected boolean		ackDeliverSm = false;

    /** Is the user using synchronous are async communication?. */
    protected boolean		asyncComms = false;


    /** Initialise a new SMPP connection object. This is a convenience
     * constructor that will create a new {@link ie.omk.smpp.net.TcpLink} object
     * using the host name and port provided.
     * @param host the hostname of the SMSC.
     * @param port the port to connect to. If 0, use the default SMPP port
     * number.
     */
    public Connection(String host, int port)
	throws java.net.UnknownHostException
    {
	if (port == 0) {
	    port = 5000; // XXX need default SMPP port.
	}

	this.link = new TcpLink(host, port);
    }

    /** Initialise a new SMPP connection object.
      * @param link The network link object to the Smsc (cannot be null)
      * @exception java.lang.NullPointerException If the link is null
      */
    public Connection(SmscLink link)
    {
	if(link == null)
	    throw new NullPointerException("Smsc Link cannot be null.");

	this.link = link;
    }

    /** Initialise a new SMPP connection object, specifying the type of
     * communication desired.
     * @param link The network link object to the Smsc (cannot be null)
     * @param async true for asyncronous communication, false for synchronous.
     * @exception java.lang.NullPointerException If the link is null
     */
    public Connection(SmscLink link, boolean async)
    {
	this(link);
	this.asyncComms = async;

	if (asyncComms) {
	    this.observers = new ArrayList();
	    createRecvThread();
	}
    }

    /** Create the receiver thread if asynchronous communications is on, does
     * nothing otherwise.
     */
    private void createRecvThread()
    {
	rcvThread = new Thread(this);
	rcvThread.setDaemon(true);
    }

    /** Set the state of this ESME.
      * @see ie.omk.smpp.Connection#getState
      */
    private synchronized void setState(int state)
    {
	this.state = state;
    }

    /** Get the current state of the ESME. One of UNBOUND, BINDING, BOUND or
      * UNBINDING.
      */
    public synchronized int getState()
    {
	return (this.state);
    }

    /** Method to open the link to the SMSC.
      * @exception java.io.IOException if an i/o error occurs.
      */
    protected void openLink()
	throws java.io.IOException
    {
	if (!this.link.isConnected()) {
	    Debug.d(this, "openLink", "Opening network link.", 1);
	    this.link.open();
	    if (this.seqNumScheme != null)
		this.seqNumScheme.reset();
	}
    }


    /** Get the interface version.
     * @see #setInterfaceVersion(SMPPVersion)
     */
    public SMPPVersion getInterfaceVersion()
    {
	return (this.interfaceVersion);
    }

    /** Set the desired interface version for this connection. The default
     * version is 3.3 (XXX soon to be 3.4). The bind operation may negotiate
     * an eariler version of the protocol if the SC does not understand the
     * version sent by the ESME. This API will not support any version eariler
     * than SMPP v3.3. The interface version is encoded as follows:
     * <table border="1" cellspacing="1" cellpadding="1">
     *   <tr><th>SMPP version</th><th>Version value</th></tr>
     *   <tr><td>v3.4</td><td>0x34</td></tr>
     *   <tr><td>v3.3</td><td>0x33</td></tr>
     *   <tr>
     *     <td colspan="2" align="center"><i>All other values reserved.</i></td>
     *   </tr>
     * </table>
     * @exception UnsupportedSMPPVersionException if <code>version</code> is
     * unsupported by this implementation.
     */
    public void setInterfaceVersion(SMPPVersion interfaceVersion)
	throws ie.omk.smpp.UnsupportedSMPPVersionException
    {
	this.interfaceVersion = interfaceVersion;
    }


    /** Set the behaviour of automatically acking ENQUIRE_LINK's from the SMSC.
      * By default, the listener thread will automatically ack an enquire_link
      * message from the Smsc so as not to lose the connection.  This
      * can be turned off with this method.
      * @param true to activate automatic acknowledgment, false to disable
      */
    public void autoAckLink(boolean b)
    {
	this.ackQryLinks = b;
    }

    /** Set the behaviour of automatically acking Deliver_Sm's from the Smsc.
      * By default the listener thread will <b>not</b> acknowledge a message.
      * @param true to activate this function, false to deactivate.
      */
    public void autoAckMessages(boolean b)
    {
	this.ackDeliverSm = b;
    }

    /** Check is this connection automatically acking Enquire link requests.
      */
    public boolean isAckingLinks()
    {
	return (ackQryLinks);
    }

    /** Check is this connection automatically acking delivered messages
      */
    public boolean isAckingMessages()
    {
	return (ackDeliverSm);
    }

    /** Acknowledge a DeliverSM command received from the Smsc. */
    public void ackDeliverSm(DeliverSM rq)
	throws java.io.IOException, ie.omk.smpp.SMPPException
    {
	DeliverSMResp rsp = new DeliverSMResp(rq);
	sendResponse(rsp);
	Debug.d(this, "ackDeliverSM", "deliver_sm_resp sent", 3);
    }

    /** Send an smpp request to the SMSC.
      * No fields in the SMPPRequest packet will be altered except for the
      * sequence number. The sequence number of the packet will be set by this
      * method according to the numbering maintained by this Connection
      * object. The numbering policy is to start at 1 and increment by 1 for
      * each packet sent.
      * @param r The request packet to send to the SMSC
      * @return The response packet returned by the SMSC, or null if
      * asynchronous communication is being used.
      * @exception java.io.IOException If a network error occurs
      * @exception java.lang.NullPointerException if <code>r</code> is null.
      */
    public SMPPResponse sendRequest(SMPPRequest r)
	throws java.io.IOException, ie.omk.smpp.SMPPException
    {
	if (link == null)
	    throw new IOException("Connection to the SMSC is not open.");

	int id = r.getCommandId();

	// Check the command is supported by the interface version..
	if (!this.interfaceVersion.isSupported(id)) {
	    throw new UnsupportedOperationException("Command ID 0x"
		    + Integer.toHexString(id)
		    + " is not supported by SMPP "
		    + this.interfaceVersion);
	}
	
	// Very few request types allowed by a receiver connection.
	if (connectionType == RECEIVER) {
	    if (id != SMPPPacket.ENQUIRE_LINK && id != SMPPPacket.UNBIND)
		throw new UnsupportedOperationException(
			"Operation not permitted "
			+ "over receiver connection");
	}

	SMPPPacket resp = null;

	if (this.seqNumScheme != null)
	    r.setSequenceNum(this.seqNumScheme.nextNumber());

	// Special packet handling..
	if (id == SMPPPacket.BIND_TRANSMITTER
		|| id == SMPPPacket.BIND_RECEIVER
		|| id == SMPPPacket.BIND_TRANSCEIVER) {
	    if (this.state != UNBOUND)
		throw new AlreadyBoundException();

	    openLink();

	    setState(BINDING);
	    if (asyncComms) {
		if (rcvThread == null)
		    createRecvThread();

		if (!rcvThread.isAlive())
		    rcvThread.start();
	    }
	}

	try {
	    id = -1;
	    link.write(r);

	    if (!asyncComms) {
		resp = readNextPacketInternal();
		id = resp.getCommandId();
		if(!(resp instanceof SMPPResponse)) {
		    Debug.d(this, "sendRequest", "packet received from "
			+ "SMSC is not an SMPPResponse!", 1);
		}
	    }
	} catch (java.net.SocketTimeoutException x) {
	    // Must set our state and re-throw the exception..
	    setState(UNBOUND);
	    throw x;
	}

	// Special!
	if (id == SMPPPacket.BIND_TRANSMITTER_RESP
		|| id == SMPPPacket.BIND_RECEIVER_RESP
		|| id == SMPPPacket.BIND_TRANSCEIVER_RESP) {
	    if (resp.getCommandStatus() == 0)
		setState(BOUND);
	}

	return ((SMPPResponse)resp);
    }

    /** Send an smpp response packet to the SMSC
      * @param r The response packet to send to the SMSC
      * @exception ie.omk.smpp.NoSuchRequestException if the response contains a
      * sequence number of a request this connection has not seen.
      * @exception java.io.IOException If a network error occurs
      */
    public void sendResponse(SMPPResponse resp)
	throws java.io.IOException, ie.omk.smpp.SMPPException
    {
	Integer key = null;

	if (link == null)
	    throw new IOException("Connection to SMSC is not valid.");

	try {
	    link.write(resp);
	} catch (java.net.SocketTimeoutException x) {
	    setState(UNBOUND);
	    throw x;
	}

	if (resp.getCommandId() == SMPPPacket.UNBIND_RESP
		&& resp.getCommandStatus() == 0)
	    setState(UNBOUND);
    }


    /** Bind this connection to the SMSC. See
     * {@link #bind(int, String, String, String, int, int, String)} for a
     * comprehensive description of this method. This overloaded version is for
     * convenience to bind to the SMSC using the default address range
     * configured at the SMSC for the binding system ID.
     */
    public BindResp bind(int type,
	    String systemID,
	    String password,
	    String systemType)
	throws java.io.IOException, ie.omk.smpp.SMPPException
    {
	return (this.bind(type, systemID, password, systemType, 0, 0, null));
    }

    /** Bind this connection to the SMSC. An application must bind to an SMSC as
     * one of transmitter, receiver or transceiver. Binding as transmitter
     * allows general manipulation of messages at the SMSC including submitting
     * messages for delivery, cancelling, replacing and querying the state of
     * previously submitted messages. Binding as a receiver allows an
     * application to receive all messages previously queued for delivery to
     * it's address. The transceiver mode, which was added in version 3.4 of the
     * SMPP protocol, combines the functionality of both transmitter and
     * receiver into one connection type.
     * <p>Note that it is only necessary to supply values for
     * <code>type, systemID</code> and <code>password</code>. The other
     * arguments may be left at null (or zero, as applicable).</p>
     * @param type connection type to use, either {@link #TRANSMITTER},
     * {@link #RECEIVER} or {@link #TRANSCEIVER}.
     * @param systemID the system ID to identify as to the SMSC.
     * @param password password to use to authenticate to the SMSC.
     * @param systemType the system type to bind as.
     * @param typeOfNum the TON of the address to bind as.
     * @param numberPlan the NPI of the address to bind as.
     * @param addrRange the address range regular expression to bind as.
     * @return the bind response packet.
     * @exception java.lang.IllegalArgumentException if a bad <code>type</code>
     * value is supplied.
     * @exception java.lang.IllegalStateException if an attempt is made to bind
     * as transceiver while using SMPP version 3.3.
     */
    public BindResp bind(int type,
	    String systemID,
	    String password,
	    String systemType,
	    int typeOfNum,
	    int numberPlan,
	    String addrRange)
	throws java.io.IOException, ie.omk.smpp.SMPPException // XXX specifics!
    {
	Bind bindReq = null;

	switch (type) {
	case TRANSMITTER:
	    bindReq = new BindTransmitter();
	    break;
	    
	case RECEIVER:
	    bindReq = new BindReceiver();
	    break;

	case TRANSCEIVER:
	    if (this.interfaceVersion.isOlder(SMPPVersion.V34)) {
		throw new IllegalStateException("Cannot bind as transceiver"
			+ " in SMPP "
			+ interfaceVersion);
	    }
	    bindReq = new BindTransceiver();
	    break;

	default:
	    throw new IllegalArgumentException("No such connection type.");
	}

	bindReq.setSystemId(systemID);
	bindReq.setPassword(password);
	bindReq.setSystemType(systemType);
	bindReq.setAddressTon(typeOfNum);
	bindReq.setAddressNpi(numberPlan);
	bindReq.setAddressRange(addrRange);
	bindReq.setInterfaceVersion(this.interfaceVersion.getVersionID());

	return ((BindResp)sendRequest(bindReq));
    }


    /** Unbind from the SMSC. This method will unbind the SMPP protocol
     * connection from the SMSC. No further SMPP operations will be possible
     * once unbound, a new bind packet will need to be send to the SMSC. Note
     * that this method will <b>not</b> close the underlying network connection.
     * @return The Unbind response packet, or null if asynchronous
     * communication is being used.
     * @exception ie.omk.smpp.NotBoundException if the connection is not yet
     * bound.
     * @exception java.io.IOException If a network error occurs.
     * @see ie.omk.smpp.SmppTransmitter#bind
     * @see ie.omk.smpp.SmppReceiver#bind
     */
    public UnbindResp unbind()
	throws java.io.IOException, ie.omk.smpp.SMPPException
    {
	if((state != BOUND) || !(link.isConnected()))
	    throw new NotBoundException();

	/* If this is set, the run() method will return when an
	 * unbind response packet arrives, stopping the listener
	 * thread. (after all observers have been notified of the packet)
	 */
	setState(UNBINDING);

	Unbind u = new Unbind();
	SMPPResponse resp = sendRequest(u);
	if (!asyncComms) {
	    if (resp.getCommandId() == SMPPPacket.UNBIND_RESP
		    && resp.getCommandStatus() == 0)
		setState(UNBOUND);
	}
	return ((UnbindResp)resp);
    }

    /** Unbind from the SMSC. This method can be used to acknowledge an unbind
      * request from the SMSC.
      * @exception ie.omk.smpp.NotBoundException if the link is currently not
      * connected.
      * @exception ie.omk.smpp.AlreadyBoundException if no unbind request has
      * been received from the SMSC.
      * @exception java.io.IOException If a network error occurs.
      * @see ie.omk.smpp.SmppTransmitter#bind
      * @see ie.omk.smpp.SmppReceiver#bind
      */
    public void unbind(UnbindResp ubr)
	throws java.io.IOException, ie.omk.smpp.SMPPException
    {
	if (state != UNBINDING)
	    throw new NotBoundException("Link is not connected.");

	if (!(link.isConnected()))
	    throw new AlreadyBoundException("No unbind request received.");

	sendResponse(ubr);
    }

    /** Use of this <b><i>highly</i></b> discouraged.
      * This is in case of emergency and stuff.
      * Closing the connection to the Smsc without unbinding
      * first can cause horrific trouble with runaway processes.  Don't
      * do it!
      */
    public void force_unbind()
	throws ie.omk.smpp.SMPPException
    {
	if(state != UNBINDING) {
	    Debug.warn(this, "force_close",
		    "Force tried before normal unbind.");
	    throw new AlreadyBoundException("Try a normal unbind first.");
	}

	Debug.d(this, "force_unbind",
		"Attempting to force the connection shut.", 4);
	try {
	    // The thread must DIE!!!!
	    if(rcvThread != null && rcvThread.isAlive()) {
		setState(UNBOUND);
		try {
		    Thread.sleep(1000);
		} catch (InterruptedException x) {
		}
		if (rcvThread.isAlive())
		    Debug.warn(this, "force_unbind",
			    "ERROR! Listener thread has not died.");
	    }

	    link.close();
	} catch(IOException ix) {
	}
	return;
    }


    /** Acknowledge an EnquireLink received from the Smsc
      * @exception java.io.IOException If a communications error occurs.
      * @exception ie.omk.smpp.SMPPExceptione XXX when?
      */
    public void ackEnquireLink(EnquireLink rq)
	throws java.io.IOException, ie.omk.smpp.SMPPException
    {
	EnquireLinkResp resp = new EnquireLinkResp(rq);
	sendResponse(resp);
	Debug.d(this, "ackEnquireLink", "responding..", 3);
    }

    /** Do a confidence check on the SMPP link to the SMSC.
      * @return The Enquire link response packet or null if asynchronous
      * communication is in use.
      * @exception java.io.IOException If a network error occurs
      * @exception ie.omk.smpp.SMPPExceptione XXX when?
      */
    public EnquireLinkResp enquireLink()
	throws java.io.IOException, ie.omk.smpp.SMPPException
    {
	EnquireLink s = new EnquireLink();
	SMPPResponse resp = sendRequest(s);
	Debug.d(this, "enquireLink", "sent enquire_link", 3);
	if (resp != null)
	    Debug.d(this, "enquireLink", "response received", 3);

	return ((EnquireLinkResp)resp);
    }

    /** Get the type of this SMPP connection.
     */
    public int getConnectionType()
    {
	return (connectionType);
    }

    /** Report whether the connection is bound or not.
      * @return true if the connection is bound
      */
    public boolean isBound()
    {
	return (state == BOUND);
    }

    /** Reset this connection's sequence numbering to 1.
      * @exception ie.omk.smpp.AlreadyBoundException if the connection is
      * currently bound to the SMSC.
      */
    public void reset()
	throws ie.omk.smpp.SMPPException
    {
	if(state == BOUND) {
	    Debug.warn(this, "reset", "Attempt to reset a bound connection.");
	    throw new AlreadyBoundException("Cannot reset connection "
		    + "while bound");
	}

	if (this.seqNumScheme != null)
	    this.seqNumScheme.reset();

	Debug.d(this, "reset", "Connection reset", 1);
    }

    /** Set the sequence numbering scheme for this connection. A sequence
     * numbering scheme determines what sequence number each SMPP packet will
     * have. By default, {@link ie.omk.smpp.util.DefaultSequenceScheme} is used,
     * which will begin with sequence number 1 and increase the number by 1 for
     * each packet thereafter.
     * <p>If the application sets the <code>scheme</code> to null, it is
     * responsible for maintaining and setting the sequence number for each SMPP
     * request it sends to the SMSC.
     * @see ie.omk.smpp.util.SequenceNumberScheme
     * @see ie.omk.smpp.message.SMPPPacket#setSequenceNum
     */
    public void setSeqNumScheme(SequenceNumberScheme scheme) {
	this.seqNumScheme = scheme;
    }

    /** Get the current sequence numbering scheme object being used by this
     * connection.
     */
    public SequenceNumberScheme getSeqNumScheme() {
	return (this.seqNumScheme);
    }

    /** Read in the next packet from the SMSC link.
      * If asynchronous communications is in use, calling this method results in
      * an SMPPException as the listener thread will be hogging the input stream
      * of the socket connection.
      * @return The next SMPP packet from the SMSC.
      * @exception java.io.IOException If an I/O error occurs.
      * @exception ie.omk.smpp.InvalidOperationException If asynchronous comms
      * is in use.
      */
    public SMPPPacket readNextPacket()
	throws java.io.IOException, ie.omk.smpp.SMPPException
    {
	if (asyncComms)
	    throw new InvalidOperationException("Asynchronous comms in use.");
	else
	    return (readNextPacketInternal());
    }


    /** Read the next packet from the SMSC link. Internal version...handles
      * special case packets like bind responses and unbind request and
      * responses.
      * @return The read SMPP packet, or null if the connection timed out.
      */
    private SMPPPacket readNextPacketInternal()
	throws java.io.IOException, ie.omk.smpp.SMPPException
    {
	SMPPPacket pak = null;
	int id = -1, st = -1;

	this.buf = link.read(this.buf);
	id = SMPPIO.bytesToInt(this.buf, 4, 4);
	pak = PacketFactory.newPacket(id);

	if (pak != null) {
	    pak.readFrom(this.buf, 0);

	    Debug.d(this, "readNextPacketInternal",
		    "Packet received: " + pak.getCommandId(), 6);

	    // Special case handling for certain packet types..
	    st = pak.getCommandStatus();
	    switch (pak.getCommandId()) {
	    case SMPPPacket.BIND_TRANSMITTER_RESP:
	    case SMPPPacket.BIND_RECEIVER_RESP:
		if (state == BINDING && st == 0)
		    setState(BOUND);
		break;

	    case SMPPPacket.UNBIND_RESP:
		if (state == UNBINDING && st == 0) {
		    Debug.d(this, "readNextPacketInternal",
			    "Successfully unbound.", 3);
		    setState(UNBOUND);
		}
		break;

	    case SMPPPacket.UNBIND:
		Debug.d(this, "readNextPacketInternal",
			"SMSC requested unbind.", 2);
		setState(UNBINDING);
		break;
	    }
	}

	return (pak);
    }


    /** Add a connection observer to receive SMPP events from this connection.
     * If this connection is not using asynchronous communication, this method
     * call has no effect.
     * @param ob the ConnectionObserver implementation to add.
     */
    public void addObserver(ConnectionObserver ob)
    {
	if (!asyncComms)
	    return;

	synchronized (observers) {
	    if (singleObserver == ob || observers.contains(ob))
		return;

	    if (singleObserver == null)
		singleObserver = ob;
	    else
		observers.add(ob);
	}
    }

    /** Remove a connection observer from this Connection.
     */
    public void removeObserver(ConnectionObserver ob)
    {
	if (!asyncComms)
	    return;

	synchronized (observers) {
	    if (observers.contains(ob))
		observers.remove(observers.indexOf(ob));
	    else if (ob == singleObserver)
		singleObserver = null;
	}
    }


    /** Notify observers of a packet received.
     * @param pak the received packet.
     */
    protected void notifyObservers(SMPPPacket pak)
    {
	// Due to multi-threading, singleObserver could be set to null (by
	// removeObserver) after we've checked that it's not. No action is
	// necessary if this happens...it just means the observer, which has
	// been removed, will not get the event.
	try {
	    if (singleObserver != null)
		singleObserver.packetReceived(this, pak);
	} catch (NullPointerException x) {
	}

	if (!observers.isEmpty()) {
	    Iterator i = observers.iterator();
	    while (i.hasNext())
		((ConnectionObserver)i.next()).packetReceived(this, pak);
	}
    }

    /** Notify observers of an SMPP control event.
     * @param event the control event to send notification of.
     */
    protected void notifyObservers(SMPPEvent event)
    {
	// Due to multi-threading, singleObserver could be set to null (by
	// removeObserver) after we've checked that it's not. No action is
	// necessary if this happens...it just means the observer, which has
	// been removed, will not get the event.
	try {
	    if (singleObserver != null)
		singleObserver.update(this, event);
	} catch (NullPointerException x) {
	}

	if (!observers.isEmpty()) {
	    Iterator i = observers.iterator();
	    while (i.hasNext())
		((ConnectionObserver)i.next()).update(this, event);
	}
    }

    /** Listener thread method for asynchronous communication.
      */
    public void run()
    {
	SMPPPacket pak = null;
	int smppEx = 0, id = 0, st = 0;
	SMPPEvent exitEvent = null;

	Debug.d(this, "run", "Listener thread started", 4);
	notifyObservers(new ReceiverStartEvent(this));
	try {
	    while (state != UNBOUND) {
		try {
		    pak = readNextPacketInternal();
		    if (pak == null) {
			// XXX Send an event to the application??
			continue;
		    }
		} catch (SMPPException x) {
		    ReceiverExceptionEvent ev =
			new ReceiverExceptionEvent(this, x, state);
		    smppEx++;
		    if (smppEx > 10) {
			Debug.d(this, "run", "Too many SMPP exceptions in "
				+ "receiver thread. Terminating.", 2);
			throw x;
		    }
		}

		id = pak.getCommandId();
		st = pak.getCommandStatus();

		// Handle special case packets..
		switch (id) {
		case SMPPPacket.DELIVER_SM:
		    if (ackDeliverSm)
			ackDelivery((DeliverSM)pak);
		    break;

		case SMPPPacket.ENQUIRE_LINK:
		    if (ackQryLinks)
			ackLinkQuery((EnquireLink)pak);
		    break;
		}

		// Tell all the observers about the new packet
		Debug.d(this, "run", "Notifying observers..", 4);
		notifyObservers(pak);
	    } // end while

	    // Notify observers that the thread is exiting with no error..
	    exitEvent = new ReceiverExitEvent(this, null, state);
	} catch (Exception x) {
	    Debug.d(this, "run", "Exception: " + x.getMessage(), 2);
	    exitEvent = new ReceiverExitEvent(this, x, state);
	    setState(UNBOUND);
	} finally {
	    // make sure other code doesn't try to restart the rcvThread..
	    rcvThread = null;
	}

	if (exitEvent != null)
	    notifyObservers(exitEvent);
    }

    private void ackDelivery(DeliverSM dm)
    {
	try {
	    Debug.d(this, "ackDelivery", "Auto acking deliver_sm "
		    + dm.getSequenceNum(), 4);
	    DeliverSMResp dmr = new DeliverSMResp(dm);
	    sendResponse(dmr);
	} catch (SMPPException x) {
	    Debug.d(this, "ackDelivery", "SMPP exception acking deliver_sm "
		    + dm.getSequenceNum(), 3);
	} catch (IOException x) {
	    Debug.d(this, "ackDelivery", "IO exception acking deliver_sm "
		    + dm.getSequenceNum(), 3);
	}
    }

    private void ackLinkQuery(EnquireLink el)
    {
	try {
	    Debug.d(this, "ackLinkEnquire", "Auto acking enquire_link "
		    + el.getSequenceNum(), 4);
	    EnquireLinkResp elr = new EnquireLinkResp(el);
	    sendResponse(elr);
	} catch (SMPPException x) {
	    Debug.d(this, "ackLinkEnquire", "SMPP exception acking "
		    + "enquire_link " + el.getSequenceNum(), 3);
	} catch (IOException x) {
	    Debug.d(this, "ackLinkEnquire", "IO exception acking enquire_link "
		    + el.getSequenceNum(), 3);
	}
    }
}