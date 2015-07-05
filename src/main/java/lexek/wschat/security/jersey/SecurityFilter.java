package lexek.wschat.security.jersey;

import lexek.wschat.chat.GlobalRole;
import lexek.wschat.db.model.UserDto;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.*;
import java.io.IOException;
import java.security.Principal;

public class SecurityFilter implements ContainerRequestFilter {
    private final GlobalRole role;

    public SecurityFilter(GlobalRole role) {
        this.role = role;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        SecurityContext securityContext = requestContext.getSecurityContext();
        Principal principal = securityContext.getUserPrincipal();
        if (principal != null && principal instanceof UserDto) {
            if (((UserDto) principal).hasRole(role)) {
                return;
            }
        }
        requestContext.abortWith(
            Response.status(Response.Status.BAD_REQUEST)
                .entity("Access denied.")
                .build());
        }
}
