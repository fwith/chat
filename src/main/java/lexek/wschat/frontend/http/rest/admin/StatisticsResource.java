package lexek.wschat.frontend.http.rest.admin;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.google.common.collect.ImmutableMap;
import lexek.wschat.chat.model.GlobalRole;
import lexek.wschat.db.dao.StatisticsDao;
import lexek.wschat.db.model.UserMessageCount;
import lexek.wschat.security.jersey.RequiredRole;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Path("/stats")
@RequiredRole(GlobalRole.ADMIN)
public class StatisticsResource {
    private final StatisticsDao statisticsDao;
    private final MetricRegistry metricRegistry;
    private final HealthCheckRegistry healthCheckRegistry;

    public StatisticsResource(StatisticsDao statisticsDao, MetricRegistry metricRegistry, HealthCheckRegistry healthCheckRegistry) {
        this.statisticsDao = statisticsDao;
        this.metricRegistry = metricRegistry;
        this.healthCheckRegistry = healthCheckRegistry;
    }

    @Path("/user/{userId}/activity")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Map<Long, Long> getUserStatistics(@PathParam("userId") long userId) {
        return statisticsDao.getUserActivity(userId);
    }

    @Path("/room/{roomId}/topChatters")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<UserMessageCount> getTopChattersForRoom(@PathParam("roomId") long roomId) {
        return statisticsDao.getTopChatters(roomId);
    }

    @Path("/global/metrics")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Map getMetrics() {
        long since = System.currentTimeMillis() - TimeUnit.MILLISECONDS.convert(24, TimeUnit.HOURS);
        return ImmutableMap.of(
            "metrics", statisticsDao.getMetrics(since)
        );
    }

    @Path("/global/runtime")
    @RequiredRole(GlobalRole.SUPERADMIN)
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Map getRuntimeMetrics() {
        return ImmutableMap.of(
            "metrics", metricRegistry.getMetrics(),
            "healthChecks", healthCheckRegistry.runHealthChecks()
        );
    }
}
