/*
 * oxTrust is available under the MIT License (2008). 
 * See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2014, Gluu
 */

package org.gluu.oxtrust.action;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.faces.context.ExternalContext;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import net.tanesha.recaptcha.ReCaptchaResponse;

import org.gluu.oxtrust.ldap.service.AttributeService;
import org.gluu.oxtrust.ldap.service.OrganizationService;
import org.gluu.oxtrust.ldap.service.PersonService;
import org.gluu.oxtrust.model.GluuCustomAttribute;
import org.gluu.oxtrust.model.GluuCustomPerson;
import org.gluu.oxtrust.model.GluuOrganization;
import org.gluu.oxtrust.model.RegistrationConfiguration;
import org.gluu.oxtrust.service.external.ExternalUserRegistrationService;
import org.gluu.oxtrust.util.OxTrustConstants;
import org.gluu.oxtrust.util.RecaptchaUtils;
import org.jboss.seam.ScopeType;
import org.jboss.seam.annotations.In;
import org.jboss.seam.annotations.Logger;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Out;
import org.jboss.seam.annotations.Scope;
import org.jboss.seam.core.Events;
import org.jboss.seam.faces.FacesMessages;
import org.jboss.seam.international.StatusMessage;
import org.jboss.seam.log.Log;
import org.xdi.config.oxtrust.ApplicationConfiguration;
import org.xdi.ldap.model.GluuStatus;
import org.xdi.model.GluuAttribute;
import org.xdi.model.GluuUserRole;

/**
 * @author Dejan Maric
 * @author Yuriy Movchan Date: 08.14.2015
 */
@Scope(ScopeType.CONVERSATION)
@Name("registerPersonAction")
public class RegisterPersonAction implements Serializable {

	private static final long serialVersionUID = 6002737004324917338L;

	@Logger
	private Log log;

	@In(value = "#{facesContext.externalContext}")
	private ExternalContext externalContext;

	@In
	private AttributeService attributeService;

	@In
	private OrganizationService organizationService;

	@In(create = true)
	@Out(scope = ScopeType.CONVERSATION)
	private CustomAttributeAction customAttributeAction;

	@In
	private FacesMessages facesMessages;

	@In
	private ExternalUserRegistrationService externalUserRegistrationService;

	private GluuCustomPerson person;

	@In
	private PersonService personService;

	@NotNull
	@Size(min = 2, max = 30, message = "Length of password should be between 2 and 30")
	private String password;

	@NotNull
	@Size(min = 2, max = 30, message = "Length of password should be between 2 and 30")
	private String repeatPassword;

	@In(value = "#{oxTrustConfiguration.applicationConfiguration}")
	private ApplicationConfiguration applicationConfiguration;

	private List<String> hiddenAttributes;

	private String inum;

	private Map<String, String[]> requestParameters = new HashMap<String, String[]>();

	private boolean captchaDisabled = false;

    private String postRegistrationInformation;

	/**
	 * Initializes attributes for registering new person
	 *
	 * @return String describing success of the operation
	 * @throws Exception
	 */
	public String initPerson() {
		String result = sanityCheck();
		if (result.equals(OxTrustConstants.RESULT_SUCCESS)) {
			this.person = (inum == null) ? new GluuCustomPerson() : personService.getPersonByInum(inum);

			boolean isPersonActiveOrDisabled = GluuStatus.ACTIVE.equals(person.getStatus()) || GluuStatus.INACTIVE.equals(person.getStatus());

			if (isPersonActiveOrDisabled) {
				result = OxTrustConstants.RESULT_NO_PERMISSIONS;
			} else {
				initAttributes();
				boolean initScriptResult = externalUserRegistrationService.executeExternalInitRegistrationMethods(this.person, requestParameters);
				result = initScriptResult ? OxTrustConstants.RESULT_SUCCESS : OxTrustConstants.RESULT_FAILURE;
			}
		}

		return result;
	}

	/**
	 * Checks if session is correct for person registration.
	 * 
	 * @return OxTrustConstants constant to be returned by action
	 */
	private String sanityCheck() {
		if (this.person != null) {
			return OxTrustConstants.RESULT_SUCCESS;
		}

		requestParameters.putAll(externalContext.getRequestParameterValuesMap());
		GluuOrganization organization = organizationService.getOrganization();
		RegistrationConfiguration config = organization.getOxRegistrationConfiguration();
		boolean registrationCustomized = config != null;

		this.captchaDisabled = registrationCustomized && config.isCaptchaDisabled();

		return OxTrustConstants.RESULT_SUCCESS;

	}

	public String register() throws CloneNotSupportedException {
		GluuOrganization organization = organizationService.getOrganization();
		RegistrationConfiguration registrationConfig = organization.getOxRegistrationConfiguration();
		boolean registrationCustomized = registrationConfig != null;
		this.captchaDisabled = registrationCustomized && registrationConfig.isCaptchaDisabled();
		ReCaptchaResponse reCaptchaResponse = null;

		if (!captchaDisabled) {
			reCaptchaResponse = RecaptchaUtils.getRecaptchaResponseFromServletContext();
		}
		boolean registrationFormValid = captchaDisabled || reCaptchaResponse != null && reCaptchaResponse.isValid() && password.equals(repeatPassword);

		if (registrationFormValid) {
			GluuCustomPerson archivedPerson = (GluuCustomPerson) person.clone();

			String customObjectClass = attributeService.getCustomOrigin();

			this.person.setCustomObjectClasses(new String[] { customObjectClass });

			// Save person
			if (person.getInum() == null) {
				String inum = personService.generateInumForNewPerson();
				this.person.setInum(inum);
			}

			if (person.getIname() == null) {
				String iname = personService.generateInameForNewPerson(this.person.getUid());
				this.person.setIname(iname);
			}

			if (person.getDn() == null) {
				String dn = personService.getDnForPerson(this.person.getInum());
				this.person.setDn(dn);
			}

			List<GluuCustomAttribute> personAttributes = this.person.getCustomAttributes();
			if (!personAttributes.contains(new GluuCustomAttribute("cn", ""))) {
				List<GluuCustomAttribute> changedAttributes = new ArrayList<GluuCustomAttribute>();
				changedAttributes.addAll(personAttributes);
				changedAttributes.add(new GluuCustomAttribute("cn", this.person.getGivenName() + " " + this.person.getSurname()));
				this.person.setCustomAttributes(changedAttributes);
			} else {
				this.person.setCommonName(this.person.getCommonName());
			}

			// save password
			this.person.setUserPassword(password);
			this.person.setOxCreationTimestamp(new Date());

			try {
				// Set default message
				this.postRegistrationInformation = "You have successfully registered with oxTrust. Login to begin your session.";

				boolean result = externalUserRegistrationService.executeExternalPreRegistrationMethods(this.person, requestParameters);
				if (!result) {
					this.person = archivedPerson;
					return OxTrustConstants.RESULT_FAILURE;
				}
				if (this.inum != null) {
					personService.updatePerson(this.person);
				} else {
					personService.addPerson(this.person);
				}
				
				result = externalUserRegistrationService.executeExternalPostRegistrationMethods(this.person, requestParameters);

				Events.instance().raiseEvent(OxTrustConstants.EVENT_PERSON_SAVED, this.person, null, null, null, null, true);
				if (!result) {
					this.person = archivedPerson;
					return OxTrustConstants.RESULT_FAILURE;
				}
			} catch (Exception ex) {
				log.error("Failed to add new person {0}", ex, this.person.getInum());
				facesMessages.add(StatusMessage.Severity.ERROR, "Failed to add new person");
				this.person = archivedPerson;
				return OxTrustConstants.RESULT_FAILURE;
			}

			return OxTrustConstants.RESULT_SUCCESS;
		}
		return OxTrustConstants.RESULT_CAPTCHA_VALIDATION_FAILED;
	}

	public void cancel() {
	}

	private void initAttributes() {
		List<GluuAttribute> allPersonAttributes = attributeService.getAllActivePersonAttributes(GluuUserRole.ADMIN);

		List<String> allAttributOrigins = attributeService.getAllAttributeOrigins(allPersonAttributes);

		GluuOrganization organization = organizationService.getOrganization();

		List<GluuCustomAttribute> customAttributes = this.person.getCustomAttributes();

		boolean isNewPerson = (customAttributes == null) || customAttributes.isEmpty();

		if (isNewPerson) {
			customAttributes = new ArrayList<GluuCustomAttribute>();
			this.person.setCustomAttributes(customAttributes);
		}

		String[] personOCs = applicationConfiguration.getPersonObjectClassTypes();
		String[] personOCDisplayNames = applicationConfiguration.getPersonObjectClassDisplayNames();
		customAttributeAction.initCustomAttributes(allPersonAttributes, customAttributes, allAttributOrigins, personOCs, personOCDisplayNames);

		List<GluuCustomAttribute> mandatoryAttributes = new ArrayList<GluuCustomAttribute>();

		RegistrationConfiguration config = organization.getOxRegistrationConfiguration();
		boolean registrationCustomized = config != null;
		boolean registrationAttributesCustomized = registrationCustomized && config.getAdditionalAttributes() != null
				&& !config.getAdditionalAttributes().isEmpty();

		if (registrationAttributesCustomized) {
			for (String attributeInum : config.getAdditionalAttributes()) {
				GluuAttribute attribute = attributeService.getAttributeByInum(attributeInum);
				GluuCustomAttribute customAttribute = new GluuCustomAttribute(attribute.getName(), "", false, false);
				mandatoryAttributes.add(customAttribute);
			}
		}
		for (GluuCustomAttribute attribute : personService.getMandatoryAtributes()) {
			if (!mandatoryAttributes.contains(attribute)) {
				mandatoryAttributes.add(attribute);
			}
		}
		mandatoryAttributes.addAll(personService.getMandatoryAtributes());

		if (isNewPerson) {
			customAttributeAction.addCustomAttributes(mandatoryAttributes);
		}

		hiddenAttributes = new ArrayList<String>();
		hiddenAttributes.add("inum");
		hiddenAttributes.add("iname");
		hiddenAttributes.add("userPassword");
		hiddenAttributes.add("gluuStatus");
		hiddenAttributes.add("oxExternalUid");
		hiddenAttributes.add("oxLastLogonTime");
	}

	/**
	 * Returns list of mandatory attributes
	 *
	 * @return list of person's mandatory attributes
	 * @throws Exception
	 */
	public List<GluuCustomAttribute> getMandatoryAttributes() {
		return personService.getMandatoryAtributes();
	}

	protected String getActionName() {
		return "registerPersonAction";
	}

	/**
	 * Returns person's attributes
	 *
	 * @return list of person's attributes
	 */
	public List<GluuCustomAttribute> getCustomAttributes() {
		return this.person.getCustomAttributes();
	}

	protected String getEventQueue() {
		return "personQueue";
	}

	public GluuCustomPerson getPerson() {
		return person;
	}

	public String getInum() {
		return inum;
	}

	public void setInum(String inum) {
		this.inum = inum;
	}

	public List<String> getHiddenAttributes() {
		return hiddenAttributes;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getRepeatPassword() {
		return repeatPassword;
	}

	public void setRepeatPassword(String repeatPassword) {
		this.repeatPassword = repeatPassword;
	}

	public boolean isCaptchaDisabled() {
		return captchaDisabled;
	}

    public String getPostRegistrationInformation() {
		return postRegistrationInformation;
	}

}
