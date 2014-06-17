package org.gluu.site.action.test.util;

import org.apache.log4j.Logger;
import org.gluu.site.action.test.AbstractAuthorizationTest;
import org.gluu.site.ldap.LDAPConnectionProvider;
import org.gluu.site.ldap.persistence.LdifDataUtility;
import org.gluu.site.test.AbstractTest;
import org.jboss.seam.contexts.Contexts;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.unboundid.ldap.sdk.LDAPConnection;

/**
 * Functional test to load data to LDAP server
 *
 * @author Yuriy Movchan Date: 08.30.2010
 */
public class LoadTestingDataTest extends AbstractAuthorizationTest {

	private static final Logger log = Logger.getLogger(LoadTestingDataTest.class);

	@BeforeTest
	public void initTestConfiguration() throws Exception {
		initTest();
	}

	@Test
	public void testReloadTestingData() throws Exception {
		new ComponentTest() {
			protected void testComponents() throws Exception {
				if (getConf().getBoolean("test.configuration.load-and-cleanup-data")) {
					log.debug("Importing new data to LDAP server...");
					LdifDataUtility ldapServerUtility = LdifDataUtility.instance();
					LDAPConnectionProvider connectionProvider = (LDAPConnectionProvider) Contexts.getApplicationContext().get("connectionProvider");
					LDAPConnection connection = connectionProvider.getConnection();
					long startImport = 0, endImport = 0;
					try {
						startImport = System.currentTimeMillis();
						String ldifFileName = AbstractTest.class.getClassLoader().getResource(getConf().getString("test.configuration.ldif-file-name")).getFile();
						ldapServerUtility.importLdifFile(connection, ldifFileName);
						endImport = System.currentTimeMillis();
					} finally {
						connectionProvider.releaseConnection(connection);
					}
					log.debug(String.format("Data imported to LDAP server within %d ms", (endImport - startImport)));
				}
			}
		}.run();
	}

}