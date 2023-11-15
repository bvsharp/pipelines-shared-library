package org.folio.rest_v2

import org.folio.models.OkapiTenant

class SystemManagementUtils extends Okapi {

    SystemManagementUtils(Object context, String okapiDomain, OkapiTenant superTenant, boolean debug = false) {
        super(context, okapiDomain, superTenant, debug)
    }

    void resetAdminUserPassword(){}
    void resetSystemUserPassword(){}

}
