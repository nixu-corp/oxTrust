/*
 * oxTrust is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2014, Gluu
 */

package org.gluu.oxtrust.model.scim;

/**
 * SCIM person Patch person groups
 * 
 * @author Reda Zerrad Date: 04.25.2012
 */
public class ScimPersonGroupsPatch extends ScimPersonGroups {
	private String operation;

	public String getOperation() {
		return this.operation;
	}

	public void setOperation(String operation) {
		this.operation = operation;
	}
}
