/* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
 * Copyright (c) 2013, 6WIND S.A. All rights reserved.                 *
 *                                                                     *
 * This file is part of the Jenkins Lockable Resources Plugin and is   *
 * published under the MIT license.                                    *
 *                                                                     *
 * See the "LICENSE.txt" file for more information.                    *
 * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */
package org.jenkins.plugins.lockableresources;

import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.AutoCompletionCandidates;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.model.Job;
import hudson.util.FormValidation;

import java.util.ArrayList;
import java.util.List;

import net.sf.json.JSONObject;

import org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.SecureGroovyScript;
import org.jenkinsci.plugins.scriptsecurity.scripts.ApprovalContext;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.CheckForNull;

public class RequiredResourcesProperty extends JobProperty<Job<?, ?>> {

	private final String resourceNames;
	private final String resourceNamesVar;
	private final String resourceNumber;
	private final String labelName;
	private final @CheckForNull SecureGroovyScript script;

	@DataBoundConstructor
	public RequiredResourcesProperty(String resourceNames,
			String resourceNamesVar, String resourceNumber,
			String labelName, SecureGroovyScript script) {
		super();
		this.resourceNames = resourceNames;
		this.resourceNamesVar = resourceNamesVar;
		this.resourceNumber = resourceNumber;
		this.labelName = labelName;
		if (script != null) {
			this.script = script.configuringWithKeyItem();
		} else {
			this.script = null;
		}
	}

	@Deprecated
	public RequiredResourcesProperty(String resourceNames,
									 String resourceNamesVar, String resourceNumber,
									 String labelName) {
		this(resourceNames, resourceNamesVar, resourceNumber, labelName, null);
	}

	private Object readResolve() {
		if (script == null && labelName != null && labelName.startsWith(LockableResource.GROOVY_LABEL_MARKER)) {
			return new RequiredResourcesProperty(resourceNames, resourceNamesVar, resourceNumber, null,
					new SecureGroovyScript(labelName.substring(LockableResource.GROOVY_LABEL_MARKER.length()), false, null)
							.configuring(ApprovalContext.create()));
		}

		return this;
	}

	public String[] getResources() {
		String names = Util.fixEmptyAndTrim(resourceNames);
		if (names != null)
			return names.split("\\s+");
		else
			return new String[0];
	}

	public String getResourceNames() {
		return resourceNames;
	}

	public String getResourceNamesVar() {
		return resourceNamesVar;
	}

	public String getResourceNumber() {
		return resourceNumber;
	}

	public String getLabelName() {
		return labelName;
	}

	public SecureGroovyScript getScript() {
		return script;
	}

	@Extension
	public static class DescriptorImpl extends JobPropertyDescriptor {

		@Override
		public String getDisplayName() {
			return "Required Lockable Resources";
		}

		@Override
		public boolean isApplicable(Class<? extends Job> jobType) {
			return AbstractProject.class.isAssignableFrom(jobType);
		}

		@Override
		public RequiredResourcesProperty newInstance(StaplerRequest req, JSONObject formData) throws FormException {
			if (formData.containsKey("required-lockable-resources")) {
				return (RequiredResourcesProperty) super.newInstance(req, formData.getJSONObject("required-lockable-resources"));
			}
			return null;
		}

		public FormValidation doCheckResourceNames(@QueryParameter String value) {
			String names = Util.fixEmptyAndTrim(value);
			if (names == null) {
				return FormValidation.ok();
			} else {
				List<String> wrongNames = new ArrayList<String>();
				for (String name : names.split("\\s+")) {
					boolean found = false;
					for (LockableResource r : LockableResourcesManager.get()
							.getResources()) {
						if (r.getName().equals(name)) {
							found = true;
							break;
						}
					}
					if (!found)
						wrongNames.add(name);
				}
				if (wrongNames.isEmpty()) {
					return FormValidation.ok();
				} else {
					return FormValidation
							.error("The following resources do not exist: "
									+ wrongNames);
				}
			}
		}

		public FormValidation doCheckLabelName(
				@QueryParameter String value,
				@QueryParameter String resourceNames,
				@QueryParameter String script) {
			String label = Util.fixEmptyAndTrim(value);
			String names = Util.fixEmptyAndTrim(resourceNames);
			String labelScript = Util.fixEmptyAndTrim(script);

			if (label == null) {
				return FormValidation.ok();
			} else if (names != null || labelScript != null) {
				return FormValidation.error(
						"Only label, label script, or resources can be defined, not all three.");
			} else {
				if (LockableResourcesManager.get().isValidLabel(label)) {
					return FormValidation.ok();
				} else {
					return FormValidation.error(
							"The label does not exist: " + label);
				}
			}
		}

		public FormValidation doCheckResourceNumber(@QueryParameter String value,
				@QueryParameter String resourceNames,
				@QueryParameter String labelName) {

			String number = Util.fixEmptyAndTrim(value);
			String names = Util.fixEmptyAndTrim(resourceNames);
			String label = Util.fixEmptyAndTrim(labelName);

			if (number == null || number.equals("") || number.trim().equals("0")) {
				return FormValidation.ok();
			}

			int numAsInt;
			try {
				numAsInt = Integer.parseInt(number);
			} catch(NumberFormatException e)  {
				return FormValidation.error(
					"Could not parse the given value as integer.");
			}
			int numResources = 0;
			if (names != null) {
				numResources = names.split("\\s+").length;
			} else if (label != null) {
				numResources = Integer.MAX_VALUE;
			}

			if (numResources < numAsInt) {
				return FormValidation.error(String.format(
					"Given amount %d is greater than amount of resources: %d.",
					numAsInt,
					numResources));
			}
			return FormValidation.ok();
		}

		public AutoCompletionCandidates doAutoCompleteLabelName(
				@QueryParameter String value) {
			AutoCompletionCandidates c = new AutoCompletionCandidates();

			value = Util.fixEmptyAndTrim(value);

			for (String l : LockableResourcesManager.get().getAllLabels())
				if (value != null && l.startsWith(value))
					c.add(l);

			return c;
		}

		public static AutoCompletionCandidates doAutoCompleteResourceNames(
				@QueryParameter String value) {
			AutoCompletionCandidates c = new AutoCompletionCandidates();

			value = Util.fixEmptyAndTrim(value);

			if (value != null) {
				for (LockableResource r : LockableResourcesManager.get()
						.getResources()) {
					if (r.getName().startsWith(value))
						c.add(r.getName());
				}
			}

			return c;
		}
	}
}

