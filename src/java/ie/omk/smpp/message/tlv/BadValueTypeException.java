package ie.omk.smpp.message.tlv;

/**
 * Attempt to set a value on a tag that expects a Java type other than that
 * used. This exception gets thrown if an attempt is made, for instance, to set
 * a <code>java.lang.String</code> value on a Tag that is defined as an
 * integer.
 * 
 * @version $Id$
 */
public class BadValueTypeException extends RuntimeException {
    /**
     * Create a new BadValueTypeException.
     */
    public BadValueTypeException() {
    }

    /**
     * Create a new BadValueTypeException.
     * 
     * @param msg
     *            Exception message.
     */
    public BadValueTypeException(String msg) {
        super(msg);
    }
}

