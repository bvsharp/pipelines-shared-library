package org.folio.rest

import org.folio.rest.model.OkapiUser

class OkapiUsersProvider {

    static OkapiUser getSuperAdmin() {
        return new OkapiUser(username: 'super_admin', password: 'admin')
    }

    static OkapiUser getTestingAdmin() {
        return new OkapiUser(username: 'testing_admin', password: 'admin')
    }

}
