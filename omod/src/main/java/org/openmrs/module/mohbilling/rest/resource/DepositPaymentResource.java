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
import org.openmrs.module.mohbilling.model.DepositPayment;
import org.openmrs.module.mohbilling.service.BillingService;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.annotation.PropertyGetter;
import org.openmrs.module.webservices.rest.web.annotation.PropertySetter;
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

@Resource(name = RestConstants.VERSION_1 + "/mohbilling/depositPayment",
        supportedClass = DepositPayment.class,
        supportedOpenmrsVersions = {"2.0 - 2.*"})
public class DepositPaymentResource extends DelegatingCrudResource<DepositPayment> {

    @Override
    protected String getUniqueId(DepositPayment delegate) {
        return String.valueOf(delegate.getDepositPaymentId());
    }

    @Override
    public DepositPayment getByUniqueId(String s) {
        return Context.getService(BillingService.class).getDepositPayment(Integer.parseInt(s));
    }

    @Override
    protected void delete(DepositPayment depositPayment, String s, RequestContext requestContext) throws ResponseException {
        depositPayment.setVoided(true);
        depositPayment.setVoidedBy(Context.getAuthenticatedUser());
        depositPayment.setVoidedDate(new Date());
        depositPayment.setVoidReason(s);
        Context.getService(BillingService.class).saveDepositPayment(depositPayment);
    }

    @Override
    public DepositPayment newDelegate() {
        return new DepositPayment();
    }

    @Override
    public DepositPayment save(DepositPayment depositPayment) {
        if (depositPayment.getCreator() == null) {
            depositPayment.setCreator(Context.getAuthenticatedUser());
        }
        if (depositPayment.getCreatedDate() == null) {
            depositPayment.setCreatedDate(new Date());
        }
        if (depositPayment.getVoided() == null) {
            depositPayment.setVoided(false);
        }
        
        // For deposit payments, if no patientBill is provided, find or create a proper patient bill
        // This is required due to database constraints
        if (depositPayment.getPatientBill() == null) {
            // Get the patient from the transaction's patient account
            org.openmrs.Patient patient = null;
            if (depositPayment.getTransaction() != null && 
                depositPayment.getTransaction().getPatientAccount() != null) {
                patient = depositPayment.getTransaction().getPatientAccount().getPatient();
            }
            
            if (patient != null) {
                // Try to find an existing deposit account bill for this patient
                org.openmrs.module.mohbilling.model.PatientBill existingBill = findExistingDepositBill(patient);
                
                if (existingBill != null) {
                    // I am using existing deposit bill here
                    depositPayment.setPatientBill(existingBill);
                } else {
                    // create a new patient bill for the patient - HUH!
                    org.openmrs.module.mohbilling.model.PatientBill patientBill = new org.openmrs.module.mohbilling.model.PatientBill();
                    patientBill.setAmount(java.math.BigDecimal.ZERO); // Deposit bills start with zero amount
                    patientBill.setIsPaid(false);
                    patientBill.setStatus("DEPOSIT_ACCOUNT");
                    patientBill.setCreator(Context.getAuthenticatedUser());
                    patientBill.setCreatedDate(new Date());
                    patientBill.setVoided(false);
                    
                    // Save the patient bill first
                    patientBill = Context.getService(BillingService.class).savePatientBill(patientBill);
                    depositPayment.setPatientBill(patientBill);
                }
            } else {
                // Fallback: create a generic bill if patient cannot be determined
                org.openmrs.module.mohbilling.model.PatientBill fallbackBill = new org.openmrs.module.mohbilling.model.PatientBill();
                fallbackBill.setAmount(java.math.BigDecimal.ZERO);
                fallbackBill.setIsPaid(false);
                fallbackBill.setStatus("DEPOSIT_GENERIC");
                fallbackBill.setCreator(Context.getAuthenticatedUser());
                fallbackBill.setCreatedDate(new Date());
                fallbackBill.setVoided(false);
                
                fallbackBill = Context.getService(BillingService.class).savePatientBill(fallbackBill);
                depositPayment.setPatientBill(fallbackBill);
            }
        }

        return Context.getService(BillingService.class).saveDepositPayment(depositPayment);
    }

    @Override
    public void purge(DepositPayment depositPayment, RequestContext requestContext) throws ResponseException {
        throw new ResourceDoesNotSupportOperationException();
    }

    @Override
    public DelegatingResourceDescription getCreatableProperties() throws ResourceDoesNotSupportOperationException {
        DelegatingResourceDescription description = new DelegatingResourceDescription();
        description.addRequiredProperty("transaction");
        description.addProperty("amountPaid");
        description.addProperty("dateReceived");
        description.addProperty("collector");
        description.addProperty("voided");
        description.addProperty("voidReason");
        // patientBill is optional for deposit payments
        description.addProperty("patientBill");
        return description;
    }

    @Override
    public DelegatingResourceDescription getRepresentationDescription(Representation representation) {
        DelegatingResourceDescription description = null;

        if (representation instanceof RefRepresentation) {
            description = new DelegatingResourceDescription();
            description.addProperty("depositPaymentId");
            description.addProperty("transaction", Representation.REF);
            description.addProperty("amountPaid");
            description.addProperty("dateReceived");
            description.addProperty("collector", Representation.REF);
            description.addProperty("creator", Representation.REF);
            description.addProperty("createdDate");
            description.addProperty("voided");
            description.addProperty("voidedBy", Representation.REF);
            description.addProperty("voidedDate");
            description.addProperty("voidReason");
            description.addSelfLink();
        } else if (representation instanceof DefaultRepresentation || representation instanceof FullRepresentation) {
            description = new DelegatingResourceDescription();
            description.addProperty("depositPaymentId");
            description.addProperty("transaction", Representation.REF);
            description.addProperty("amountPaid");
            description.addProperty("dateReceived");
            description.addProperty("collector", Representation.REF);
            description.addProperty("creator", Representation.REF);
            description.addProperty("createdDate");
            description.addProperty("voided");
            description.addProperty("voidedBy", Representation.REF);
            description.addProperty("voidedDate");
            description.addProperty("voidReason");
            description.addSelfLink();
            if (representation instanceof DefaultRepresentation) {
                description.addLink("full", ".?v=" + RestConstants.REPRESENTATION_FULL);
            }
        }

        return description;
    }

    /**
     * Property getter for creator name
     */
    @PropertyGetter("creator")
    public String getCreator(DepositPayment depositPayment) {
        if (depositPayment.getCreator() != null) {
            return depositPayment.getCreator().getDisplayString();
        }
        return null;
    }

    /**
     * Property getter for collector name
     */
    @PropertyGetter("collector")
    public String getCollector(DepositPayment depositPayment) {
        if (depositPayment.getCollector() != null) {
            return depositPayment.getCollector().getDisplayString();
        }
        return null;
    }

    /**
     * Property getter for voidedBy name
     */
    @PropertyGetter("voidedBy")
    public String getVoidedBy(DepositPayment depositPayment) {
        if (depositPayment.getVoidedBy() != null) {
            return depositPayment.getVoidedBy().getDisplayString();
        }
        return null;
    }

    /**
     * Property setter for transaction
     */
    @PropertySetter("transaction")
    public static void setTransaction(DepositPayment depositPayment, Object value) {
        if (value instanceof java.util.Map) {
            java.util.Map<String, Object> transactionMap = (java.util.Map<String, Object>) value;
            
            // Create a new Transaction object
            org.openmrs.module.mohbilling.model.Transaction transaction = new org.openmrs.module.mohbilling.model.Transaction();
            
            // Set basic properties
            if (transactionMap.get("amount") != null) {
                transaction.setAmount(new java.math.BigDecimal(transactionMap.get("amount").toString()));
            }
            if (transactionMap.get("reason") != null) {
                transaction.setReason(transactionMap.get("reason").toString());
            }
            if (transactionMap.get("transactionDate") != null) {
                try {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                    transaction.setTransactionDate(sdf.parse(transactionMap.get("transactionDate").toString()));
                } catch (Exception e) {
                    // Try alternative date format
                    try {
                        java.text.SimpleDateFormat sdf2 = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
                        transaction.setTransactionDate(sdf2.parse(transactionMap.get("transactionDate").toString()));
                    } catch (Exception e2) {
                        transaction.setTransactionDate(new java.util.Date());
                    }
                }
            }
            
            // Set collector
            if (transactionMap.get("collector") != null) {
                java.util.Map<String, Object> collectorMap = (java.util.Map<String, Object>) transactionMap.get("collector");
                if (collectorMap.get("uuid") != null) {
                    org.openmrs.User collector = Context.getUserService().getUserByUuid(collectorMap.get("uuid").toString());
                    transaction.setCollector(collector);
                }
            }
            
            // Set patient account
            if (transactionMap.get("patientAccount") != null) {
                java.util.Map<String, Object> accountMap = (java.util.Map<String, Object>) transactionMap.get("patientAccount");
                if (accountMap.get("patientAccountId") != null) {
                    org.openmrs.module.mohbilling.model.PatientAccount account = 
                        Context.getService(BillingService.class).getPatientAccount(Integer.parseInt(accountMap.get("patientAccountId").toString()));
                    transaction.setPatientAccount(account);
                }
            }
            
            // Set creator and created date
            transaction.setCreator(Context.getAuthenticatedUser());
            transaction.setCreatedDate(new java.util.Date());
            transaction.setVoided(false);
            
            depositPayment.setTransaction(transaction);
        }
    }

    /**
     * Property setter for collector
     */
    @PropertySetter("collector")
    public static void setCollector(DepositPayment depositPayment, Object value) {
        if (value instanceof java.util.Map) {
            java.util.Map<String, Object> collectorMap = (java.util.Map<String, Object>) value;
            if (collectorMap.get("uuid") != null) {
                org.openmrs.User collector = Context.getUserService().getUserByUuid(collectorMap.get("uuid").toString());
                depositPayment.setCollector(collector);
            }
        }
    }

    /**
     * Property setter for amountPaid
     */
    @PropertySetter("amountPaid")
    public static void setAmountPaid(DepositPayment depositPayment, Object value) {
        if (value != null) {
            depositPayment.setAmountPaid(new java.math.BigDecimal(value.toString()));
        }
    }

    /**
     * Property setter for dateReceived
     */
    @PropertySetter("dateReceived")
    public static void setDateReceived(DepositPayment depositPayment, Object value) {
        if (value != null) {
            try {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                depositPayment.setDateReceived(sdf.parse(value.toString()));
            } catch (Exception e) {
                try {
                    java.text.SimpleDateFormat sdf2 = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
                    depositPayment.setDateReceived(sdf2.parse(value.toString()));
                } catch (Exception e2) {
                    depositPayment.setDateReceived(new java.util.Date());
                }
            }
        }
    }

    /**
     * Property setter for patientBill (optional for deposit payments)
     */
    @PropertySetter("patientBill")
    public static void setPatientBill(DepositPayment depositPayment, Object value) {
        if (value instanceof java.util.Map) {
            java.util.Map<String, Object> billMap = (java.util.Map<String, Object>) value;
            if (billMap.get("patientBillId") != null) {
                org.openmrs.module.mohbilling.model.PatientBill patientBill = 
                    Context.getService(BillingService.class).getPatientBill(Integer.parseInt(billMap.get("patientBillId").toString()));
                depositPayment.setPatientBill(patientBill);
            }
        } else if (value instanceof String) {
            try {
                org.openmrs.module.mohbilling.model.PatientBill patientBill = 
                    Context.getService(BillingService.class).getPatientBill(Integer.parseInt(value.toString()));
                depositPayment.setPatientBill(patientBill);
            } catch (NumberFormatException e) {
                // Ignore invalid patient bill ID
            }
        }
        // If value is null or invalid, patientBill remains null (which is allowed for deposit payments)
    }

    @Override
    protected PageableResult doGetAll(RequestContext context) throws ResponseException {
        return new NeedsPaging<>(Context.getService(BillingService.class).getAllDepositPayments(), context);
    }

    /**
     * Helper method to find an existing deposit account bill for a patient
     */
    private org.openmrs.module.mohbilling.model.PatientBill findExistingDepositBill(org.openmrs.Patient patient) {
        try {
            // Get all patient bills and find one with DEPOSIT_ACCOUNT status
            java.util.List<org.openmrs.module.mohbilling.model.PatientBill> allBills = 
                Context.getService(BillingService.class).getAllPatientBills();
            
            for (org.openmrs.module.mohbilling.model.PatientBill bill : allBills) {
                if ("DEPOSIT_ACCOUNT".equals(bill.getStatus()) && 
                    !bill.getVoided() && 
                    bill.getCreator() != null) {
                    // This is a deposit account bill, we can reuse it
                    return bill;
                }
            }
        } catch (Exception e) {
            // If there's an error finding existing bills, return null to create a new one
            System.err.println("Error finding existing deposit bill: " + e.getMessage());
        }
        
        return null;
    }
}
