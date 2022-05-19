package se.ltu.workflow.smartproduct.arrowhead;

import java.net.InetSocketAddress;
import java.util.Objects;

import se.arkalix.security.identity.SystemIdentity;

/**
 * Basic representation of an Arrowhead system following interface {@code SystemDescription} in Kalix library.
 *
 */
public class AFSystem {
    
    String name;
    private final SystemIdentity identity;
    private final InetSocketAddress remoteSocketAddress;
    
    /**
     * Constructs an instance of  {@code AFSystem} using a system's name, x.509 certificate and IP address.
     * 
     * @param name  The name of the System, for user convenience as it does not need to be unique, not null
     * @param identity  The unique x.509 certificate associated to this system, not null
     * @param remoteSocketAddress  The IP Socket Address (IP address + port number) or (hostname +port number)
     * where the system is connected, not null
     */
    public AFSystem(String name, SystemIdentity identity, InetSocketAddress remoteSocketAddress) {
        this.name = Objects.requireNonNull(name, "Expected system name");
        this.identity = Objects.requireNonNull(identity,"Expected system certificate");
        this.remoteSocketAddress = Objects.requireNonNull(remoteSocketAddress,
                                                            "Expected system IP Socket Address");
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((identity == null) ? 0 : identity.hashCode());
        return result;
    }

    /**
     * Two Arrowhead Framework systems are equal if they have the same x.509 certificate
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        AFSystem other = (AFSystem) obj;
        if (identity == null) {
            if (other.identity != null)
                return false;
        } else if (!identity.equals(other.identity))
            return false;
        return true;
    }
    
    
}
