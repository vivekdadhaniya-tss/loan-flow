package com.loanflow.integration;

import com.loanflow.exception.CreditBureauException;
import com.loanflow.integration.dto.CreditBureauResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CreditBureauClient {

    private final RestTemplate restTemplate;
    private final CreditBureauProperties properties;

    private static final String REPORT_ENDPOINT = "/report";

    public CreditBureauResponse fetchReport(String panNumber) {
        // 1. Append the PAN number as a query parameter in the URL
        String url = properties.getBaseUrl() + REPORT_ENDPOINT + "?panNumber={panNumber}";

        log.info("Calling Credit Bureau (GET) for PAN: {}", panNumber);

        try {
            // 2. GET requests do not have a body, so we pass 'null' or just the headers
            HttpEntity<Void> entity = new HttpEntity<>(getHeader());

            // 3. to make a GET request with headers and URL variables
            ResponseEntity<CreditBureauResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    CreditBureauResponse.class,     // deserialization
                    panNumber // This automatically replaces {panNumber} in the URL
            );

            log.info("Bureau response received for PAN: {}", panNumber);
            return response.getBody();

        } catch (RestClientException e) {
            log.warn("Credit Bureau unavailable for PAN: {}. Reason: {}", panNumber, e.getMessage());
            throw new CreditBureauException("Failed to fetch credit bureau report: " + e.getMessage());
        }
    }

    private HttpHeaders getHeader() {
        HttpHeaders headers = new HttpHeaders();
        // GET requests don't send content, they 'accept' content, so we use setAccept instead of setContentType
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }
}