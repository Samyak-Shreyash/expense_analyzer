package com.expenseanalyzer.merchant.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MerchantNormalizerTest {

    private final MerchantNormalizer normalizer = new MerchantNormalizer();

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            "UPI/AMZN/ABC123/AMAZON PAY             | Amazon",
            "UPI-SWIGGY-ORDER                       | Swiggy",
            "POS 4521XXXX1234 ZOMATO LTD BANGALORE  | Zomato",
            "Netflix.com Mumbai                     | Netflix",
            "UPI/123456789012/FLIPKART INTERNET/okaxis | Flipkart",
            "ACH D- BIGBASKET 0000123456             | BigBasket",
            "UBER EATS INDIA                        | Uber Eats",
            "UBER INDIA SYSTEMS TRIP                | Uber",
            "IRCTC E-TICKETING                      | IRCTC",
            "upi/blinkit/9876543210@ybl             | Blinkit",
            "AIRTEL PREPAID RECHARGE                | Airtel",
            "JIO MOBILITY                           | Jio",
            "BOOKMYSHOW MUMBAI                      | BookMyShow",
            "DOMINOS PIZZA                          | Domino's",
            "STARBUCKS COFFEE                       | Starbucks",
            "SPOTIFY INDIA                          | Spotify",
            "ZEPTO MARKETPLACE                      | Zepto",
            "MYNTRA DESIGNS                         | Myntra",
            "OLA CABS                               | Ola",
            "PAYTM*SWIGGY                           | Swiggy"
    })
    void knownMerchantVariantsMapToCanonicalName(String raw, String expected) {
        MerchantMatch m = normalizer.normalize(raw.trim());
        assertThat(m.canonicalName()).isEqualTo(expected);
        assertThat(m.confidence()).isEqualByComparingTo(MerchantNormalizer.RULE_CONFIDENCE);
        assertThat(m.categoryHint()).isNotBlank();
    }

    @Test
    void personToPersonUpiTransferGetsNoMerchant() {
        MerchantMatch m = normalizer.normalize("UPI/9876543210@ybl/RAMESH KUMAR/Payment from Phone");
        assertThat(m.canonicalName()).isNull();
        assertThat(m.confidence()).isNull();
        assertThat(m.normalizedDescription()).isEqualTo("RAMESH KUMAR PAYMENT FROM PHONE");
    }

    @Test
    void bankNoiseIsRemovedFromNormalizedDescription() {
        MerchantMatch m = normalizer.normalize("NEFT-HDFC0001234-SALARY JAN  REF 99887766");
        assertThat(m.canonicalName()).isNull();
        assertThat(m.normalizedDescription()).isEqualTo("SALARY JAN");
    }

    @Test
    void aliasMustMatchAWholeToken() {
        assertThat(normalizer.normalize("AMAZONIA RESTAURANT").canonicalName()).isNull();
        assertThat(normalizer.normalize("OLAF STORE").canonicalName()).isNull();
    }

    @Test
    void descriptionThatIsOnlyNoiseFallsBackToCollapsedRaw() {
        MerchantMatch m = normalizer.normalize("UPI/123456789012");
        assertThat(m.canonicalName()).isNull();
        assertThat(m.normalizedDescription()).isEqualTo("UPI/123456789012");
    }

    @Test
    void rulesAreVersioned() {
        assertThat(MerchantNormalizer.RULES_VERSION).isPositive();
    }
}
