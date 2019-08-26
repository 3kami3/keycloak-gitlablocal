# keycloak-gitlablocal

Keycloak Social Login extension for GitLabLocal.


## Install

Download `keycloak-gitlablocal-ear-<version>.ear` from [Releases page](https://github.com/3kami3/keycloak-gitlablocal/releases).
Then deploy it into `$KEYCLOAK_HOME/standalone/deployments/` directory.

## Setup

### (local) GitLab

You must create an Application in GitLab so that Keycloak can make an OAuth connection between itself and GitLab.
1. Login to your locally installed GitLab.
2. Go to the user's `Settings` page.
3. Click on the `Applications` link in the vertical navigation.
4. This section of the GitLab UI will allow you to create a new OAuth Application, with a set of allowed scopes and a generated `Client Id` and `Client Secret`.
5. Simply fill out the form with appropriate values and click `Save application` when you are done.

You will need to provide the following information in the form:
* Name - Keycloak
* Redirect URI - https://keycloak.example.com/auth/realms/example/broker/gitlablocal/endpoint
* Scopes - api, read_user, openid

Once you click the `Save application` button, the application will be created and you will be shown the generated `Client Id` and `Client Secret`.

### Keycloak

1. Open `Themes` setting page in `master` realm. Then set `gitlablocal` theme as `Admin Console Theme`. Note: You may need to re-login for reloading the theme.
2. Add `gitlablocal` Identity Provider in the realm which you want to configure.
3. In the `gitlablocal` identity provider page, set `GitLab Site URL` and `Application Id` and `Application Secret`.


## Source Build

Clone this repository and run `mvn package`.
You can see `keycloak-gitlablocal-ear-<version>.ear` under `ear/target` directory.


## Licence

[Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0)


## Author

- [Koichiro Mikami](https://github.com/3kami3)

## Acknowledgments

I referred to the following project. Thank you.
- [keycloak-discord](https://github.com/wadahiro/keycloak-discord) - Keycloak Social Login extension for Discord
