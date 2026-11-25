package com.j2ee.tour.security;

import com.j2ee.tour.common.ApiResponse;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Stream;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = StaffRouteSecurityTest.RouteProbe.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, StaffRouteSecurityTest.RouteProbe.class})
class StaffRouteSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean JwtService jwt;
    @MockitoBean FixedWindowRateLimiter limiter;

    record Route(String method, String path, boolean staff, boolean customer) {}
    record Case(Route route, String role) {}

    static Stream<Case> routes() {
        return Stream.of(
                shared("GET", "/api/admin/bookings"),
                shared("GET", "/api/admin/payments"),
                shared("POST", "/api/admin/payments/1/reconcile"),
                shared("GET", "/api/admin/customers"),
                shared("GET", "/api/admin/customers/1/bookings"),
                shared("GET", "/api/customers/1"),
                shared("GET", "/api/admin/departures"),
                shared("GET", "/api/admin/departures/1/passengers"),
                shared("GET", "/api/payments"), shared("GET", "/api/payments/1"),
                shared("GET", "/api/bookings/1/payments"), shared("POST", "/api/payments"),
                shared("GET", "/api/invoices"), shared("GET", "/api/invoices/1"),
                shared("GET", "/api/invoices/by-booking/1"),
                new Route("GET", "/api/bookings", true, true),
                new Route("GET", "/api/bookings/1", true, true),
                new Route("GET", "/api/bookings/code/BK-1", true, true),
                new Route("POST", "/api/bookings/1/confirm", true, true),
                new Route("POST", "/api/bookings/1/complete", true, true),
                new Route("POST", "/api/bookings/1/cancel", true, true),
                new Route("POST", "/api/bookings", false, true),
                new Route("PUT", "/api/bookings/1", false, true),
                new Route("DELETE", "/api/bookings/1", false, true),
                admin("GET", "/api/admin/stats"), admin("GET", "/api/admin/reports"),
                admin("GET", "/api/admin/accounts"), admin("GET", "/api/accounts"),
                admin("PUT", "/api/accounts/1"), admin("GET", "/api/account-roles"),
                admin("PUT", "/api/account-roles/1"), admin("GET", "/api/admin/tours"),
                admin("POST", "/api/admin/tours"), admin("PUT", "/api/admin/tours/1"),
                admin("DELETE", "/api/admin/tours/1"),
                admin("GET", "/api/tour-departures"), admin("POST", "/api/tour-departures"),
                admin("PUT", "/api/tour-departures/1"), admin("DELETE", "/api/tour-departures/1"),
                admin("GET", "/api/tour-guides"), admin("POST", "/api/accommodations"),
                admin("GET", "/api/travel-vehicles"), admin("GET", "/api/tourist-destinations"),
                admin("POST", "/api/tour-images"), admin("POST", "/api/admin/images/upload"),
                admin("GET", "/api/promotions"), admin("POST", "/api/promotions"),
                admin("PUT", "/api/customers/1"), admin("DELETE", "/api/customers/1"),
                admin("POST", "/api/customers"), admin("POST", "/api/travelers"),
                admin("GET", "/api/refunds"), admin("POST", "/api/payments/1/refunds"),
                admin("PUT", "/api/payments/1"), admin("DELETE", "/api/payments/1"),
                admin("POST", "/api/bookings/1/payments/callback"),
                admin("POST", "/api/invoices"), admin("PUT", "/api/invoices/1"),
                admin("DELETE", "/api/invoices/1"),
                admin("POST", "/api/admin/bookings"),
                admin("DELETE", "/api/admin/payments/1"),
                admin("GET", "/api/admin/departures/1/internal"),
                admin("GET", "/api/admin/departures/internal/passengers"),
                admin("GET", "/api/admin/customers/export/bookings"),
                admin("POST", "/api/admin/payments/bulk/reconcile"),
                admin("GET", "/api/admin/future-feature"))
                .flatMap(route -> Stream.of("anonymous", "ROLE_USER", "ROLE_STAFF", "ROLE_ADMIN")
                        .map(role -> new Case(route, role)));
    }

    private static Route shared(String method, String path) { return new Route(method, path, true, false); }
    private static Route admin(String method, String path) { return new Route(method, path, false, false); }

    @ParameterizedTest(name = "{0}")
    @MethodSource("routes")
    void enforcesMethodAndPathAllowList(Case test) throws Exception {
        var route = test.route();
        var req = request(HttpMethod.valueOf(route.method()), route.path());
        int expected;
        if ("anonymous".equals(test.role())) {
            expected = 401;
        } else {
            when(jwt.parse(test.role())).thenReturn(new AuthPrincipal(7L, "demo", test.role()));
            req.header("Authorization", "Bearer " + test.role());
            expected = "ROLE_ADMIN".equals(test.role())
                    || ("ROLE_STAFF".equals(test.role()) ? route.staff() : route.customer()) ? 200 : 403;
        }
        mvc.perform(req).andExpect(status().is(expected));
    }

    // Test-only handlers isolate the real filter chain from business state. The future-feature
    // handler proves default denial even when a new admin controller is subsequently registered.
    @RestController
    static class RouteProbe {
        @RequestMapping({"/api/admin/**", "/api/accounts/**", "/api/account-roles/**",
                "/api/bookings/**", "/api/payments/**", "/api/customers/**", "/api/invoices/**",
                "/api/refunds/**", "/api/tour-departures/**", "/api/tour-guides/**",
                "/api/accommodations/**", "/api/travel-vehicles/**", "/api/tourist-destinations/**",
                "/api/tour-images/**", "/api/promotions/**", "/api/travelers/**"})
        ApiResponse<String> ok() { return ApiResponse.success("allowed"); }
    }
}
