package ie.omk.smpp.message.tlv;

/**
 * An attempt was made to encode or decode a value with a length outside of the
 * bounds defined by its <code>Tag</code>. This can happen, for instance,
 * when an attempt is made to encode a string value that is longer than the
 * maximum length defined by the tag for that value.
 * 
 * @version $Id$
 */
public class InvalidSizeForValueException extends RuntimeException {
    /**
     * Create a new InvalidSizeForValueException.
     */
    public InvalidSizeForValueException() {
    }

    /**
     * Create a new InvalidSizeForValueException.
     * 
     * @param msg
     *            The exception message.
     */
    public InvalidSizeForValueException(String msg) {
        super(msg);
    }
}
