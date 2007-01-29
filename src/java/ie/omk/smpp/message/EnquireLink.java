package ie.omk.smpp.message;

/**
 * Check the link status. This message can originate from either an ESME or the
 * SMSC. It is used to check that the entity at the other end of the link is
 * still alive and responding to messages. Usually used by the SMSC after a
 * period of inactivity to decide whether to close the link.
 * 
 * @author Oran Kelly
 * @version 1.0
 */
public class EnquireLink extends SMPPPacket {
    /**
     * Construct a new EnquireLink.
     */
    public EnquireLink() {
        super(ENQUIRE_LINK);
    }
}

