package com.clabriapp.mscalabriapp.infrastructure.adapters.rest;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class HealthResourceTest {

    @Test
    void healthRespondeUp() {
        given()
                .when().get("/health")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }

    @Test
    void livenessYReadinessRespondenUp() {
        given().when().get("/health/live")
                .then().statusCode(200).body("status", equalTo("UP"));

        given().when().get("/health/ready")
                .then().statusCode(200).body("status", equalTo("UP"));
    }
}
