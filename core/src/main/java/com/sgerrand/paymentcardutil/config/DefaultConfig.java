package com.sgerrand.paymentcardutil.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Mastercard IPM message and parameter file layout.
 *
 * <p>Generated from the config of the Python cardutil package, version
 * 0.7.3. Do not edit by hand; see tools/gen_config.py.
 *
 * @see <a href="https://github.com/adelosa/cardutil">adelosa/cardutil</a>
 */
final class DefaultConfig {

    private DefaultConfig() {
    }

    static IsoConfig build() {
        return IsoConfig.builder()
                .maxVbsRecordLength(6000)
                .bitConfig(bitConfig())
                .outputDataElements(outputDataElements())
                .parameterTables(parameterTables())
                .build();
    }

    private static Map<Integer, FieldConfig> bitConfig() {
        Map<Integer, FieldConfig> fields = new LinkedHashMap<>();
        fields.put(1, new FieldConfig(
                "Bitmap secondary", FieldType.FIXED, 8, ValueType.TEXT, null, FieldProcessor.NONE, null));
        fields.put(2, new FieldConfig(
                "PAN", FieldType.LLVAR, 0, ValueType.TEXT, null, FieldProcessor.NONE, null));
        fields.put(3, new FieldConfig(
                "Processing code", FieldType.FIXED, 6, ValueType.TEXT, null, FieldProcessor.NONE, null));
        fields.put(4, new FieldConfig(
                "Amount transaction", FieldType.FIXED, 12, ValueType.LONG, null, FieldProcessor.NONE, null));
        fields.put(5, new FieldConfig(
                "Amount, Reconciliation", FieldType.FIXED, 12, ValueType.LONG, null, FieldProcessor.NONE, null));
        fields.put(6, new FieldConfig(
                "Amount, Cardholder billing", FieldType.FIXED, 12, ValueType.LONG, null, FieldProcessor.NONE, null));
        fields.put(9, new FieldConfig(
                "Conversion rate, Reconciliation", FieldType.FIXED, 8, ValueType.LONG, null, FieldProcessor.NONE, null));
        fields.put(10, new FieldConfig(
                "Conversion rate, Cardholder billing", FieldType.FIXED, 8, ValueType.LONG, null, FieldProcessor.NONE, null));
        fields.put(12, new FieldConfig(
                "Date/Time local transaction", FieldType.FIXED, 12, ValueType.DATETIME, "%y%m%d%H%M%S", FieldProcessor.NONE, null));
        fields.put(14, new FieldConfig(
                "Expiration date", FieldType.FIXED, 4, ValueType.TEXT, null, FieldProcessor.NONE, null));
        fields.put(22, new FieldConfig(
                "Point of service data code", FieldType.FIXED, 12, ValueType.TEXT, null, FieldProcessor.NONE, null));
        fields.put(23, new FieldConfig(
                "Card sequence number", FieldType.FIXED, 3, ValueType.TEXT, null, FieldProcessor.NONE, null));
        fields.put(24, new FieldConfig(
                "Function code", FieldType.FIXED, 3, ValueType.TEXT, null, FieldProcessor.NONE, null));
        fields.put(25, new FieldConfig(
                "Message reason code", FieldType.FIXED, 4, ValueType.TEXT, null, FieldProcessor.NONE, null));
        fields.put(26, new FieldConfig(
                "Card acceptor business code", FieldType.FIXED, 4, ValueType.LONG, null, FieldProcessor.NONE, null));
        fields.put(30, new FieldConfig(
                "Amounts, original", FieldType.FIXED, 24, ValueType.TEXT, null, FieldProcessor.NONE, null));
        fields.put(31, new FieldConfig(
                "Acquirer reference data", FieldType.LLVAR, 23, ValueType.TEXT, null, FieldProcessor.NONE, null));
        fields.put(32, new FieldConfig(
                "Acquiring institution ID code", FieldType.LLVAR, 0, ValueType.TEXT, null, FieldProcessor.NONE, null));
        fields.put(33, new FieldConfig(
                "Forwarding institution ID code", FieldType.LLVAR, 0, ValueType.TEXT, null, FieldProcessor.NONE, null));
        fields.put(37, new FieldConfig(
                "Retrieval reference number", FieldType.FIXED, 12, ValueType.TEXT, null, FieldProcessor.NONE, null));
        fields.put(38, new FieldConfig(
                "Approval code", FieldType.FIXED, 6, ValueType.TEXT, null, FieldProcessor.NONE, null));
        fields.put(40, new FieldConfig(
                "Service code", FieldType.FIXED, 3, ValueType.TEXT, null, FieldProcessor.NONE, null));
        fields.put(41, new FieldConfig(
                "Card acceptor terminal ID", FieldType.FIXED, 8, ValueType.TEXT, null, FieldProcessor.NONE, null));
        fields.put(42, new FieldConfig(
                "Card acceptor Id", FieldType.FIXED, 15, ValueType.TEXT, null, FieldProcessor.NONE, null));
        fields.put(43, new FieldConfig(
                "Card acceptor name/location", FieldType.LLVAR, 0, ValueType.TEXT, null, FieldProcessor.DE43, "(?P<DE43_NAME>.+?) *\\\\(?P<DE43_ADDRESS>.+?) *\\\\(?P<DE43_SUBURB>.+?) *\\\\(?P<DE43_POSTCODE>.{10})(?P<DE43_STATE>.{3})(?P<DE43_COUNTRY>\\S{3})$"));
        fields.put(48, new FieldConfig(
                "Additional data", FieldType.LLLVAR, 0, ValueType.TEXT, null, FieldProcessor.PDS, null));
        fields.put(49, new FieldConfig(
                "Currency code, Transaction", FieldType.FIXED, 3, ValueType.TEXT, null, FieldProcessor.NONE, null));
        fields.put(50, new FieldConfig(
                "Currency code, Reconciliation", FieldType.FIXED, 3, ValueType.TEXT, null, FieldProcessor.NONE, null));
        fields.put(51, new FieldConfig(
                "Currency code, Cardholder billing", FieldType.FIXED, 3, ValueType.TEXT, null, FieldProcessor.NONE, null));
        fields.put(54, new FieldConfig(
                "Amounts, additional", FieldType.LLLVAR, 0, ValueType.TEXT, null, FieldProcessor.NONE, null));
        fields.put(55, new FieldConfig(
                "ICC system related data", FieldType.LLLVAR, 255, ValueType.TEXT, null, FieldProcessor.ICC, "on_error=WARN"));
        fields.put(62, new FieldConfig(
                "Additional data 2", FieldType.LLLVAR, 0, ValueType.TEXT, null, FieldProcessor.PDS, null));
        fields.put(63, new FieldConfig(
                "Transaction lifecycle Id", FieldType.LLLVAR, 16, ValueType.TEXT, null, FieldProcessor.NONE, null));
        fields.put(71, new FieldConfig(
                "Message number", FieldType.FIXED, 8, ValueType.LONG, null, FieldProcessor.NONE, null));
        fields.put(72, new FieldConfig(
                "Data record", FieldType.LLLVAR, 0, ValueType.TEXT, null, FieldProcessor.NONE, null));
        fields.put(73, new FieldConfig(
                "Date, Action", FieldType.FIXED, 6, ValueType.TEXT, null, FieldProcessor.NONE, null));
        fields.put(93, new FieldConfig(
                "Transaction destination institution ID", FieldType.LLVAR, 0, ValueType.TEXT, null, FieldProcessor.NONE, null));
        fields.put(94, new FieldConfig(
                "Transaction originator institution ID", FieldType.LLVAR, 0, ValueType.TEXT, null, FieldProcessor.NONE, null));
        fields.put(95, new FieldConfig(
                "Card issuer reference data", FieldType.LLVAR, 10, ValueType.TEXT, null, FieldProcessor.NONE, null));
        fields.put(100, new FieldConfig(
                "Receiving institution ID", FieldType.LLVAR, 11, ValueType.TEXT, null, FieldProcessor.NONE, null));
        fields.put(105, new FieldConfig(
                "Multi-Use Transaction Identification Data", FieldType.LLLVAR, 0, ValueType.TEXT, null, FieldProcessor.NONE, null));
        fields.put(111, new FieldConfig(
                "Amount, currency conversion assignment", FieldType.LLLVAR, 0, ValueType.TEXT, null, FieldProcessor.NONE, null));
        fields.put(123, new FieldConfig(
                "Additional data 3", FieldType.LLLVAR, 0, ValueType.TEXT, null, FieldProcessor.PDS, null));
        fields.put(124, new FieldConfig(
                "Additional data 4", FieldType.LLLVAR, 0, ValueType.TEXT, null, FieldProcessor.PDS, null));
        fields.put(125, new FieldConfig(
                "Additional data 5", FieldType.LLLVAR, 0, ValueType.TEXT, null, FieldProcessor.PDS, null));
        fields.put(127, new FieldConfig(
                "Network data", FieldType.LLLVAR, 0, ValueType.TEXT, null, FieldProcessor.NONE, null));
        return fields;
    }

    private static List<String> outputDataElements() {
        return List.of(
                "MTI", "DE2", "DE3", "DE4", "DE12",
                "DE14", "DE22", "DE23", "DE24", "DE25",
                "DE26", "DE30", "DE31", "DE33", "DE37",
                "DE38", "DE40", "DE41", "DE42", "DE48",
                "DE49", "DE50", "DE63", "DE71", "DE73",
                "DE93", "DE94", "DE95", "DE100", "PDS0023",
                "PDS0052", "PDS0122", "PDS0148", "PDS0158", "PDS0165",
                "DE43_NAME", "DE43_SUBURB", "DE43_POSTCODE", "ICC_DATA");
    }

    private static Map<String, ParamTable> parameterTables() {
        Map<String, ParamTable> tables = new LinkedHashMap<>();
        tables.put("IP0006T1", tableIP0006T1());
        tables.put("IP0040T1", tableIP0040T1());
        tables.put("IP0075T1", tableIP0075T1());
        tables.put("IP0095T1", tableIP0095T1());
        return tables;
    }

    private static ParamTable tableIP0006T1() {
        Map<String, ParamTable.Position> fields = new LinkedHashMap<>();
        fields.put("card_program_id", new ParamTable.Position(19, 22));
        fields.put("data_element_id", new ParamTable.Position(22, 25));
        fields.put("data_element_name", new ParamTable.Position(25, 82));
        fields.put("data_element_format", new ParamTable.Position(82, 85));
        fields.put("data_element_minimum_length", new ParamTable.Position(85, 88));
        fields.put("data_element_mastercard_maximum_length", new ParamTable.Position(88, 91));
        fields.put("data_element_iso_maximum_length", new ParamTable.Position(91, 94));
        fields.put("de_lll_size", new ParamTable.Position(94, 95));
        fields.put("data_element_subfields", new ParamTable.Position(95, 97));
        return new ParamTable("IP0006T1", fields);
    }

    private static ParamTable tableIP0040T1() {
        Map<String, ParamTable.Position> fields = new LinkedHashMap<>();
        fields.put("issuer_account_range_low", new ParamTable.Position(19, 38));
        fields.put("gcms_product_id", new ParamTable.Position(38, 41));
        fields.put("issuer_account_range_high", new ParamTable.Position(41, 60));
        fields.put("card_program_identifier", new ParamTable.Position(60, 63));
        fields.put("issuer_card_program_identifier_priority_code", new ParamTable.Position(63, 65));
        fields.put("member_id", new ParamTable.Position(65, 76));
        fields.put("product_type_id", new ParamTable.Position(76, 77));
        fields.put("endpoint", new ParamTable.Position(77, 84));
        fields.put("card_country_alpha", new ParamTable.Position(84, 87));
        fields.put("card_country_numeric", new ParamTable.Position(87, 90));
        fields.put("region", new ParamTable.Position(90, 91));
        fields.put("product_class", new ParamTable.Position(91, 94));
        fields.put("transaction_routing_indicator", new ParamTable.Position(94, 95));
        fields.put("first_presentment_reassignment_switch", new ParamTable.Position(95, 96));
        fields.put("product_reassignment_switch", new ParamTable.Position(96, 97));
        fields.put("pwcb_opt_in_switch", new ParamTable.Position(97, 98));
        fields.put("licenced_product_id", new ParamTable.Position(98, 101));
        fields.put("mapping_service_ind", new ParamTable.Position(101, 102));
        fields.put("alm_participation_ind", new ParamTable.Position(102, 103));
        fields.put("alm_activation_date", new ParamTable.Position(103, 109));
        fields.put("cardholder_billing_currency_default", new ParamTable.Position(109, 112));
        fields.put("cardholder_billing_currency_exponent_default", new ParamTable.Position(112, 113));
        fields.put("cardholder_bill_primary_currency", new ParamTable.Position(113, 141));
        fields.put("chip_to_magnetic_conversion_service_indicator", new ParamTable.Position(141, 142));
        fields.put("floor_expiration_date", new ParamTable.Position(142, 148));
        fields.put("co_brand_participation_switch", new ParamTable.Position(148, 149));
        fields.put("spend_control_switch", new ParamTable.Position(149, 150));
        fields.put("merchant_cleansing_service_participation", new ParamTable.Position(150, 153));
        fields.put("merchant_cleansing_activation_date", new ParamTable.Position(153, 159));
        fields.put("paypass_enabled_indicator", new ParamTable.Position(159, 160));
        fields.put("regulated_rate_type_indicator", new ParamTable.Position(160, 161));
        fields.put("psn_route_indicator", new ParamTable.Position(161, 162));
        fields.put("cash_back_without_purchase_indicator", new ParamTable.Position(162, 163));
        fields.put("repower_reload_participation_indicator", new ParamTable.Position(164, 165));
        fields.put("moneysend_indicator", new ParamTable.Position(165, 166));
        fields.put("durban_regulated_rate_indicator", new ParamTable.Position(166, 167));
        fields.put("cash_access_only_participating_indicator", new ParamTable.Position(167, 168));
        fields.put("authentication_indicator", new ParamTable.Position(168, 169));
        return new ParamTable("IP0040T1", fields);
    }

    private static ParamTable tableIP0075T1() {
        Map<String, ParamTable.Position> fields = new LinkedHashMap<>();
        fields.put("card_acceptor_business_code_mcc", new ParamTable.Position(19, 24));
        fields.put("card_acceptor_business_cab_program", new ParamTable.Position(24, 28));
        fields.put("card_acceptor_business_cab_program_life_cycle_indicator", new ParamTable.Position(28, 29));
        fields.put("card_acceptor_business_cab_type", new ParamTable.Position(29, 30));
        fields.put("card_acceptor_business_cab_life_cycle_indicator", new ParamTable.Position(30, 31));
        return new ParamTable("IP0075T1", fields);
    }

    private static ParamTable tableIP0095T1() {
        Map<String, ParamTable.Position> fields = new LinkedHashMap<>();
        fields.put("card_program_identifier", new ParamTable.Position(19, 22));
        fields.put("business_service_arrangement_type", new ParamTable.Position(22, 23));
        fields.put("business_service_id_code", new ParamTable.Position(23, 29));
        fields.put("interchange_rate_designator_ird", new ParamTable.Position(29, 31));
        fields.put("card_acceptor_business_cab_program", new ParamTable.Position(31, 35));
        fields.put("life_cycle_indicator", new ParamTable.Position(35, 36));
        return new ParamTable("IP0095T1", fields);
    }
}
