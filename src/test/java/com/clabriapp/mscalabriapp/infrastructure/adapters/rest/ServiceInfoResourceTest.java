package com.clabriapp.mscalabriapp.infrastructure.adapters.rest;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class ServiceInfoResourceTest {

    @Test
    void exponeLaInformacionDelServicioCableadaPorPuertos() {
        given()
                .when().get("/api/v1/service-info")
                .then()
                .statusCode(200)
                .body("name", equalTo("msCalabriapp"))
                .body("displayName", equalTo("ClabriApp"))
                .body("version", equalTo("0.1.0-SNAPSHOT"));
    }
}
