package org.folio.rest

import hudson.AbortException
import groovy.json.JsonOutput
import org.folio.rest.model.GeneralParameters
import org.folio.rest.model.OkapiTenant
import org.folio.rest.model.OkapiUser

class ServicePoints extends GeneralParameters {

    private Users users = new Users(steps, okapiUrl)

    private Authorization auth = new Authorization(steps, okapiUrl)

    ServicePoints(Object steps, String okapiUrl) {
        super(steps, okapiUrl)
    }

    /**
     * Get ids of service points
     * @param tenant
     * @return
     */
    def getServicePointsIds(OkapiTenant tenant, OkapiUser user) {
        auth.getOkapiToken(tenant, user)
        String url = okapiUrl + "/service-points"
        ArrayList headers = [[name: 'X-Okapi-Tenant', value: tenant.getId()],
                             [name: 'X-Okapi-Token', value: tenant.getAdmin_user().getToken() ? tenant.getAdmin_user().getToken() : '', maskValue: true]]
        def res = http.getRequest(url, headers, true)
        if (res.status == HttpURLConnection.HTTP_OK) {
            return tools.jsonParse(res.content).servicepoints*.id
        } else {
            return []
        }
    }

    /**
     * Get service points users record
     * @param tenant
     * @param user
     * @return
     */
    def getServicePointsUsersRecords(OkapiTenant tenant, OkapiUser user) {
        users.validateUser(user)
        auth.getOkapiToken(tenant, user)
        String url = okapiUrl + "/service-points-users?query=userId%3d%3d" + user.uuid
        ArrayList headers = [[name: 'X-Okapi-Tenant', value: tenant.getId()],
                             [name: 'X-Okapi-Token', value: tenant.getAdmin_user().getToken() ? tenant.getAdmin_user().getToken() : '', maskValue: true]]
        def res = http.getRequest(url, headers, true)
        if (res.status == HttpURLConnection.HTTP_OK) {
            return tools.jsonParse(res.content)
        } else {
            throw new AbortException("Can not get user credentials." + http.buildHttpErrorMessage(res))
        }
    }
    /**
     * @param tenant
     * @param user
     * @param servicePointsIds
     */
    void createServicePointsUsersRecord(OkapiTenant tenant, OkapiUser user, ArrayList servicePointsIds) {
        users.validateUser(user)
        auth.getOkapiToken(tenant, user)
        String url = okapiUrl + "/service-points-users"
        ArrayList headers = [[name: 'Content-type', value: "application/json"],
                             [name: 'X-Okapi-Tenant', value: tenant.getId()],
                             [name: 'X-Okapi-Token', value: tenant.getAdmin_user().getToken() ? tenant.getAdmin_user().getToken() : '', maskValue: true]]
        if (servicePointsIds) {
            logger.info("Assign service points ${servicePointsIds.join(", ")} to user ${user.username}")
            String body = JsonOutput.toJson([userId               : user.uuid,
                                             servicePointsIds     : servicePointsIds,
                                             defaultServicePointId: servicePointsIds.first()])
            def res = http.postRequest(url, body, headers, true)
            if (res.status == HttpURLConnection.HTTP_CREATED) {
                logger.info("Service points ${servicePointsIds.join(", ")} successfully assigned to user ${user.username}")
            } else if (res.status == 422) {
                logger.warning("Unable to proceed request." + http.buildHttpErrorMessage(res))
            } else {
                throw new AbortException("Can not set credentials for user ${user.username}." + http.buildHttpErrorMessage(res))
            }
        } else {
            throw new AbortException("Service points ids list is empty")
        }
    }
}
