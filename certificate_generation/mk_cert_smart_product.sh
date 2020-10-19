cd "$(dirname "$0")" || exit
source "lib_certs.sh"
cd ../smart-product/src/main/resources/certificates
pwd

# Creates certificates for Workflow manager with the certificates
# in /src/main/resources/certificates/master for the root of Arrowhead
# and /src/main/resources/certificates/cloud for the Local Cloud

create_system_keystore \
  "master/master.p12" "arrowhead.eu" \
  "cloud/testcloud2.p12" "testcloud2.aitia.arrowhead.eu" \
  "smart_product.p12" "smart_product" \
  "dns:localhost,ip:127.0.0.1"

#create_truststore \
#  "cloud-relay/crypto/truststore.p12" \
#  "cloud-root/crypto/root.crt" "arrowhead.eu"

