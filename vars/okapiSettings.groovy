import org.folio.Constants
import org.folio.rest.model.Email
import org.folio.rest.model.OkapiTenant
import org.folio.rest.model.OkapiUser

def tenant(Map args = [:]) {
    return new OkapiTenant(
        id: args.tenantId,
        name: args.tenantName,
        description: args.tenantDescription,
        parameters: [
            loadReference: args.loadReference, loadSample: args.loadSample
        ]
    )
}

def defaultAdminUser() {
    return new OkapiUser(
        'diku_admin',
        'admin',
        'DIKU',
        'ADMINISTRATOR',
        'admin@diku.example.org'
    )
}

def adminUser(String name, String password, String firstName, String lastName, String email) {
    return new OkapiUser(
        username: name,
        password: password,
        firstName: firstName,
        lastName: lastName,
        email: email,
        groupName: 'staff',
        permissions: ["perms.users.assign.immutable", "perms.users.assign.mutable", "perms.users.assign.okapi", "perms.all"]
    )
}

def email() {
    withCredentials([[$class           : 'AmazonWebServicesCredentialsBinding',
                      credentialsId    : Constants.AWS_CREDENTIALS_ID,
                      accessKeyVariable: 'EMAIL_USERNAME',
                      secretKeyVariable: 'EMAIL_PASSWORD']]) {
        return new Email(
            smtpHost: Constants.EMAIL_SMTP_SERVER,
            smtpPort: Constants.EMAIL_SMTP_PORT,
            from: Constants.EMAIL_FROM,
            username: EMAIL_USERNAME,
            password: EMAIL_PASSWORD
        )
    }
}
