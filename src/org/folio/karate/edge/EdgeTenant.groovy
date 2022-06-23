package org.folio.karate.edge

import com.cloudbees.groovy.cps.NonCPS

class EdgeTenant {

    String tenant

    String user

    String password

    @NonCPS
    boolean equals(o) {
        if (this.is(o)) return true
        if (getClass() != o.class) return false

        EdgeTenant that = (EdgeTenant) o

        if (password != that.password) return false
        if (tenant != that.tenant) return false
        if (user != that.user) return false

        return true
    }

    @NonCPS
    int hashCode() {
        int result
        result = (tenant != null ? tenant.hashCode() : 0)
        result = 31 * result + (user != null ? user.hashCode() : 0)
        result = 31 * result + (password != null ? password.hashCode() : 0)
        return result
    }


    @Override
    public String toString() {
        return "EdgeTenant{" +
            "tenant='" + tenant + '\'' +
            ", userName='" + user + '\'' +
            ", password='" + password + '\'' +
            '}';
    }
}
