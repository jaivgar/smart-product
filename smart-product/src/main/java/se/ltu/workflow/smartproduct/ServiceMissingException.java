package se.ltu.workflow.smartproduct;

public class ServiceMissingException extends Exception {


    private static final long serialVersionUID = 1L;
    
    /**
     * Constructs a <code>StartWorfklowWE</code> with no detail message.
     */
    public ServiceMissingException() {
        super();
    }

    /**
     * Constructs a <code>StartWorfklowWE</code> with the specified
     * detail message.
     *
     * @param   s   the detail message.
     */
    public ServiceMissingException(String s) {
        super(s);
    }

}
