package org.openmrs.module.mohbilling.rest.resource;

import java.util.List;
import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.module.mohbilling.model.Consommation;
import org.openmrs.module.mohbilling.model.PatientBill;
import org.openmrs.module.mohbilling.service.BillingProcessingService;
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
import org.openmrs.module.webservices.rest.web.resource.impl.AlreadyPaged;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingCrudResource;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingResourceDescription;
import org.openmrs.module.webservices.rest.web.resource.impl.NeedsPaging;
import org.openmrs.module.webservices.rest.web.response.ResponseException;
import org.openmrs.module.webservices.rest.web.annotation.PropertySetter;
import org.openmrs.module.webservices.rest.web.response.ConversionException;

@Resource(name = RestConstants.VERSION_1 + "/mohbilling/patientBill",
        supportedClass = PatientBill.class,
        supportedOpenmrsVersions = {"2.0 - 2.*"})
@JsonIgnoreProperties(ignoreUnknown = true)
public class PatientBillResource extends DelegatingCrudResource<PatientBill> {

    @Override
    public PatientBill getByUniqueId(String uniqueId) {
        return Context.getService(BillingProcessingService.class).getPatientBillById(Integer.parseInt(uniqueId));
    }

    @Override
    public PatientBill newDelegate() {
        return new PatientBill();
    }

    @Override
    public PatientBill save(PatientBill delegate) {
        return Context.getService(BillingProcessingService.class).savePatientBill(delegate);
    }

    @Override
    protected void delete(PatientBill delegate, String reason, RequestContext context) throws ResponseException {
        Context.getService(BillingProcessingService.class).voidPatientBill(delegate, Context.getAuthenticatedUser(), null, reason);
    }

    @Override
    public void purge(PatientBill delegate, RequestContext context) throws ResponseException {
        // Implementation not required unless we'd need to permanently delete bills
    }

    @Override
    protected String getUniqueId(PatientBill delegate) {
        return String.valueOf(delegate.getPatientBillId());
    }

    @Override
    public DelegatingResourceDescription getRepresentationDescription(Representation rep) {
        if (rep instanceof DefaultRepresentation || rep instanceof RefRepresentation) {
            DelegatingResourceDescription description = new DelegatingResourceDescription();
            description.addProperty("patientBillId");
            description.addProperty("amount");
            description.addProperty("createdDate");
            description.addProperty("status");
            description.addProperty("voided");
            description.addProperty("payments", Representation.REF);
            description.addProperty("phoneNumber");
            description.addProperty("transactionStatus");
            description.addProperty("paymentConfirmedDate");
            description.addProperty("creator");

            // NEWLY ADDED FIELDS FROM `Consommation`
            description.addProperty("departmentName");
            description.addProperty("policyIdNumber");
            description.addProperty("beneficiaryName");
            description.addProperty("insuranceName");

            description.addSelfLink();
            return description;
        }
        return null;
    }

    @PropertyGetter("creator")
    public String getCreator(PatientBill bill) {
        if (bill.getCreator() != null && bill.getCreator().getPerson() != null) {
            return bill.getCreator().getPerson().getPersonName().getFullName();
        }
        return null;
    }

    @PropertyGetter("departmentName")
    public String getDepartmentName(PatientBill bill) {
        Consommation cons = Context.getService(BillingService.class).getConsommationByPatientBill(bill);
        return (cons != null && cons.getDepartment() != null) ? cons.getDepartment().getName() : null;
    }

    @PropertyGetter("policyIdNumber")
    public String getPolicyIdNumber(PatientBill bill) {
        Consommation cons = Context.getService(BillingService.class).getConsommationByPatientBill(bill);
        return (cons != null && cons.getBeneficiary() != null) ? cons.getBeneficiary().getPolicyIdNumber() : null;
    }

    @PropertyGetter("beneficiaryName")
    public String getBeneficiaryName(PatientBill bill) {
        Consommation cons = Context.getService(BillingService.class).getConsommationByPatientBill(bill);
        if (cons != null && cons.getBeneficiary() != null && cons.getBeneficiary().getPatient() != null) {
            return cons.getBeneficiary().getPatient().getPersonName().getFullName();
        }
        return null;
    }

    @PropertyGetter("insuranceName")
    public String getInsuranceName(PatientBill bill) {
        Consommation cons = Context.getService(BillingService.class).getConsommationByPatientBill(bill);
        return (cons != null && cons.getBeneficiary() != null &&
                cons.getBeneficiary().getInsurancePolicy() != null &&
                cons.getBeneficiary().getInsurancePolicy().getInsurance() != null) ?
                cons.getBeneficiary().getInsurancePolicy().getInsurance().getName() : null;
    }

    @Override
    public DelegatingResourceDescription getCreatableProperties() {
        DelegatingResourceDescription description = new DelegatingResourceDescription();
        description.addRequiredProperty("amount");
        description.addRequiredProperty("status");
        return description;
    }

    @Override
    public DelegatingResourceDescription getUpdatableProperties() {
        DelegatingResourceDescription description = new DelegatingResourceDescription();
        description.addRequiredProperty("amount");
        description.addRequiredProperty("status");
        return description;
    }

    @Override
    protected PageableResult doSearch(RequestContext context) {
        BillingProcessingService service = Context.getService(BillingProcessingService.class);
        Integer startIndex = context.getStartIndex();
        Integer limit = context.getLimit();
        String orderBy = context.getRequest().getParameter("orderBy");
        String orderDirection = context.getRequest().getParameter("order");
        List<PatientBill> bills = service.getPatientBillsByPagination(
                startIndex,
                limit,
                orderBy,
                orderDirection
        );
        return new AlreadyPaged<>(context, bills, false);
    }

    @Override
    protected PageableResult doGetAll(RequestContext context) throws ResponseException {
        return doSearch(context);
    }

    @PropertySetter("amount")
    public void setAmount(PatientBill instance, Object value) {
        try {
            if (value == null) {
                instance.setAmount(null);
            } else if (value instanceof BigDecimal) {
                instance.setAmount((BigDecimal) value);
            } else if (value instanceof Number) {
                instance.setAmount(new BigDecimal(value.toString()));
            } else if (value instanceof String) {
                instance.setAmount(new BigDecimal((String) value));
            } else {
                throw new ConversionException("Unable to convert " + value.getClass() + " to BigDecimal");
            }
        } catch (Exception e) {
            throw new ConversionException("Unable to convert " + value + " to BigDecimal: " + e.getMessage());
        }
    }
}
