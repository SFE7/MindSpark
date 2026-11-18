import secrets
import string
import os

def gen_pwd(length=32):
    chars = string.ascii_letters + string.digits
    return ''.join(secrets.choice(chars) for _ in range(length))

def main():
    host_ip = "192.168.0.134"
    app_db_pwd = gen_pwd(32)
    keycloak_db_pwd = gen_pwd(32)
    keycloak_admin_pwd = gen_pwd(32)
    pgadmin_pwd = gen_pwd(32)

    env_content = f"""HOST_IP={host_ip}
APP_DB_USER=mindspark
APP_DB_PASSWORD={app_db_pwd}
KEYCLOAK_DB_USER=keycloak
KEYCLOAK_DB_PASSWORD={keycloak_db_pwd}
KEYCLOAK_ADMIN=admin
KEYCLOAK_ADMIN_PASSWORD={keycloak_admin_pwd}
PGADMIN_DEFAULT_EMAIL=admin@mindspark.local
PGADMIN_DEFAULT_PASSWORD={pgadmin_pwd}
KEYCLOAK_ISSUER_URI=http://{host_ip}:8180/realms/mindspark
KEYCLOAK_JWK_SET_URI=http://keycloak:8080/realms/mindspark/protocol/openid-connect/certs
CORS_ALLOWED_ORIGINS=http://localhost:4200,http://{host_ip}:4200
"""

    example_content = """HOST_IP=192.168.0.134
APP_DB_USER=mindspark
APP_DB_PASSWORD=CHANGE_ME
KEYCLOAK_DB_USER=keycloak
KEYCLOAK_DB_PASSWORD=CHANGE_ME
KEYCLOAK_ADMIN=admin
KEYCLOAK_ADMIN_PASSWORD=CHANGE_ME
PGADMIN_DEFAULT_EMAIL=admin@mindspark.local
PGADMIN_DEFAULT_PASSWORD=CHANGE_ME
KEYCLOAK_ISSUER_URI=http://192.168.0.134:8180/realms/mindspark
KEYCLOAK_JWK_SET_URI=http://keycloak:8080/realms/mindspark/protocol/openid-connect/certs
CORS_ALLOWED_ORIGINS=http://localhost:4200,http://192.168.0.134:4200
"""

    with open(".env", "w", encoding="utf-8") as f:
        f.write(env_content)

    with open(".env.example", "w", encoding="utf-8") as f:
        f.write(example_content)

    print("SUCCESS: .env and .env.example written without printing secrets.")

if __name__ == "__main__":
    main()
