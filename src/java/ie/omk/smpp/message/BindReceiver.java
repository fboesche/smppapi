package ie.omk.smpp.message;

/**
 * Bind to the SMSC as receiver. This message is used to bind to the SMSC as a
 * Receiver ESME.
 * 
 * @author Oran Kelly
 * @version 1.0
 */
public class BindReceiver extends ie.omk.smpp.message.Bind {
    /**
     * Constructs a new BindReceiver.
     */
    public BindReceiver() {
        super(BIND_RECEIVER);
    }
}

