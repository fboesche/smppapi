/*
 * Java implementation of the SMPP v3.3 API
 * Copyright (C) 1998 - 2000 by Oran Kelly
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
 * Java SMPP API author: oran.kelly@ireland.com
 */
package ie.omk.smpp;

import ie.omk.smpp.message.*;
import ie.omk.debug.Debug;

public class SmppEvent
{
// File identifier string: used for debug output
	private static String FILE = "SmppEvent";

	// The source object of this event
	Object		source;

	// Command Id of the packet causing the event
	int			cmdId;

	// Status of the Smpp command
	int			status;

	// Sequence number of the Smpp command
	int			seqNo;

	Object		infoClass;

	// This is the packet that caused the event.
	SMPPPacket	packet;


	/** Construct a new Smpp event.
	  * @param source The source of this event (not null)
	  * @param o Extra details to associate with the event (see getDetails()) (can be null)
	  * @param p The packet that caused the event (not null)
	  * @see SmppEvent#getDetails
	  */
	public SmppEvent(Object source, Object o, SMPPPacket p)
	{
		if(p == null || source == null)
			throw new NullPointerException();

		this.source = source;
		this.cmdId = p.getCommandId();
		this.status = p.getCommandStatus();
		this.seqNo = p.getSeqNo();
		this.packet = p;
		this.infoClass = o;
	}
	
	/** Construct a new Smpp event.
	  * @param source The source of this event (not null)
	  * @param cmdid The command Id of this event
	  * @param status The status to associate with the event
	  * @param seqno The sequence number of the command associated with this event
	  * @param o Extra details to associate with the event (see getDetails()) (can be null)
	  * @param p The packet that caused the event (can be null)
	  * @see SmppEvent#getDetails
	  */
	public SmppEvent(Object source,	// Source of the event
					 int cmdid,		// Command Id
					 int status,	// Status of the command
					 int seqno,		// Sequence no.
					 Object o,		// Additional information
					 SMPPPacket p)	// The packet that caused the event
	{
		if(source == null)
			throw new NullPointerException();

		this.source = source;
		this.cmdId = cmdid;
		this.status = status;
		this.seqNo = seqNo;
		this.infoClass = o;
		this.packet = p;
	}

	/** Get the source of this event */
	public Object getSource()
	{
		return source;
	}

	/** Make an Object of appropriate type for inclusion in an SmppEvent.
	  * @param p The packet to generate a details Object for.
	  * @see SmppEvent#getDetails
	  */
	public static Object detailFactory(SMPPPacket p)
	{
		if(p instanceof DeliverSM)
			return new MessageDetails((DeliverSM)p);
		else if(p instanceof QuerySMResp)
			return new MessageDetails((QuerySMResp)p);
		else if(p instanceof QueryMsgDetailsResp)
			return new MessageDetails((QueryMsgDetailsResp)p);

		else if(p instanceof QueryLastMsgsResp)
			return ((QueryLastMsgsResp)p).getMessageIds();

		else if(p instanceof BindTransmitterResp)
			return ((BindTransmitterResp)p).getSystemId();
		else if(p instanceof BindReceiverResp)
			return ((BindReceiverResp)p).getSystemId();
		else if(p instanceof SubmitSMResp)
			return new Integer(((SubmitSMResp)p).getMessageId());
		else if(p instanceof SubmitMultiResp)
			return new Integer(((SubmitMultiResp)p).getMessageId());
		else if(p instanceof ParamRetrieveResp)
			return ((ParamRetrieveResp)p).getParamValue();
		else
			return null;
	}
	
	/** Additional details.  This can take the form of one of the following:
	 *  Object type							Smpp Command
	 *  -----------							------------
	 * class MessageDetails					QuerySMResp
 	 *										QueryMsgDetailsResp
	 *										DeliverSM
	 * java.lang.String						BindTransmitterResp
	 *										BindReceiverResp
	 *										ParamRetrieveResp
	 * java.lang.Integer					SubmitSMResp
	 *										SubmitMultiResp
	 * int[]								QueryLastMsgsResp
	 * null									GenericNack
	 *										UnbindResp
	 *										CancelSMResp
	 *										ReplaceSMResp
	 *										EnquireLink
	 *										EnquireLinkResp
     *
	 * (All others return null, but will not usually be part of an
	 *  Smpp event)
	 */
	public Object getDetails()
	{
		return infoClass;
	}

	/** Get the command Id associated with this event. */
	public int getCommandId()
	{
		return cmdId;
	}

	/** Get the status of the command associated with this event */
	public int getStatus()
	{
		return status;
	}

	/** Get the sequence number of the command associated with this event */ 
	public int getSeqNo()
	{
		return seqNo;
	}

	public SMPPPacket getPacket()
	{
		return packet;
	}
}
