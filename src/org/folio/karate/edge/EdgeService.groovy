package org.folio.karate.edge

import com.cloudbees.groovy.cps.NonCPS

class EdgeService {

    String name

    List<EdgeTenant> tenants

    EdgeService(def jsonContents) {
        this.name = jsonContents.name
        jsonContents.tenants { tenant ->
            EdgeTenant edgeTenant = new EdgeTenant(tenant: tenant.tenant, user: tenant.user, password: tenant.password)
            tenants.add(edgeTenant)
        }
    }

    @NonCPS
    boolean equals(o) {
        if (this.is(o)) return true
        if (getClass() != o.class) return false

        EdgeService that = (EdgeService) o

        if (name != that.name) return false
        if (tenants != that.tenants) return false

        return true
    }

    @NonCPS
    int hashCode() {
        int result
        result = (name != null ? name.hashCode() : 0)
        result = 31 * result + (tenants != null ? tenants.hashCode() : 0)
        return result
    }


    @Override
    public String toString() {
        return "EdgeService{" +
            "name='" + name + '\'' +
            ", tenants=" + tenants +
            '}';
    }
}
