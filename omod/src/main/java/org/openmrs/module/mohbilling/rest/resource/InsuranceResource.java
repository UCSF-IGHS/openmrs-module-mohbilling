/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 * <p>
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.mohbilling.rest.resource;

import java.util.Date;

import org.openmrs.api.context.Context;
import org.openmrs.module.mohbilling.model.Insurance;
import org.openmrs.module.mohbilling.model.InsuranceRate;
import org.openmrs.module.mohbilling.service.BillingService;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.annotation.PropertyGetter;
import org.openmrs.module.webservices.rest.web.annotation.Resource;
import org.openmrs.module.webservices.rest.web.representation.DefaultRepresentation;
import org.openmrs.module.webservices.rest.web.representation.FullRepresentation;
import org.openmrs.module.webservices.rest.web.representation.RefRepresentation;
import org.openmrs.module.webservices.rest.web.representation.Representation;
import org.openmrs.module.webservices.rest.web.resource.api.PageableResult;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingCrudResource;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingResourceDescription;
import org.openmrs.module.webservices.rest.web.resource.impl.NeedsPaging;
import org.openmrs.module.webservices.rest.web.response.ResourceDoesNotSupportOperationException;
import org.openmrs.module.webservices.rest.web.response.ResponseException;

@Resource(name = RestConstants.VERSION_1 + "/mohbilling/insurance",
        supportedClass = Insurance.class,
        supportedOpenmrsVersions = {"2.0 - 2.*"})
public class InsuranceResource extends DelegatingCrudResource<Insurance> {

    @Override
    protected String getUniqueId(Insurance delegate) {
        return String.valueOf(delegate.getInsuranceId());
    }

    @Override
    public Insurance getByUniqueId(String insuranceId) {
        return Context.getService(BillingService.class).getInsurance(Integer.parseInt(insuranceId));
    }

    @Override
    protected void delete(Insurance insurance, String s, RequestContext requestContext) throws ResponseException {
        throw new ResourceDoesNotSupportOperationException();
    }

    @Override
    public Insurance newDelegate() {
        return new Insurance();
    }

    @Override
    public Insurance save(Insurance insurance) {
        if (insurance.getCreator() == null) {
            insurance.setCreator(Context.getAuthenticatedUser());
        }

        if (insurance.getCreatedDate() == null) {
            insurance.setCreatedDate(new Date());
        }

        Context.getService(BillingService.class).saveInsurance(insurance);
        return insurance;
    }

    @Override
    public void purge(Insurance insurance, RequestContext requestContext) throws ResponseException {
        throw new ResourceDoesNotSupportOperationException();
    }

    @PropertyGetter("rate")
    public Float getRate(Insurance insurance) {
        InsuranceRate currentRate = insurance.getCurrentRate();
        return (currentRate != null) ? currentRate.getRate() : null;
    }

    @PropertyGetter("flatFee")
    public String getFlatFee(Insurance insurance) {
        InsuranceRate currentRate = insurance.getCurrentRate();
        return (currentRate != null && currentRate.getFlatFee() != null) ?
                currentRate.getFlatFee().toString() : null;
    }


    @PropertyGetter("voided")
    public Boolean getVoided(Insurance insurance) {
        return insurance.isVoided();
    }

    @Override
    public DelegatingResourceDescription getCreatableProperties() throws ResourceDoesNotSupportOperationException {
        DelegatingResourceDescription description = new DelegatingResourceDescription();
        description.addProperty("name");
        description.addProperty("address");
        description.addProperty("phone");
        description.addProperty("concept");
        description.addProperty("rates");
        description.addProperty("categories");
        return description;
    }

    @Override
    public DelegatingResourceDescription getRepresentationDescription(Representation representation) {
        DelegatingResourceDescription description = null;

        if (representation instanceof RefRepresentation) {
            description = new DelegatingResourceDescription();
            description.addProperty("insuranceId");
            description.addProperty("name");
            description.addProperty("address");
            description.addProperty("phone");
            description.addProperty("category");
            description.addProperty("rate");
            description.addProperty("flatFee");
            description.addSelfLink();
        } else if (representation instanceof DefaultRepresentation) {
            description = new DelegatingResourceDescription();
            description.addProperty("insuranceId");
            description.addProperty("name");
            description.addProperty("address");
            description.addProperty("phone");
            description.addProperty("category");
            description.addProperty("rate");
            description.addProperty("flatFee");
            description.addSelfLink();
            description.addLink("full", ".?v=" + RestConstants.REPRESENTATION_FULL);
        } else if (representation instanceof FullRepresentation) {
            description = new DelegatingResourceDescription();
            description.addProperty("insuranceId");
            description.addProperty("name");
            description.addProperty("address");
            description.addProperty("phone");
            description.addProperty("category");
            description.addProperty("rate");
            description.addProperty("flatFee");

            description.addProperty("concept", Representation.REF);
            description.addProperty("creator", Representation.REF);
            description.addProperty("createdDate");
            description.addProperty("voided");
            description.addSelfLink();
        }

        return description;
    }

    @Override
    protected PageableResult doGetAll(RequestContext context) throws ResponseException {
        return new NeedsPaging<>(Context.getService(BillingService.class).getAllInsurances(), context);
    }
}
