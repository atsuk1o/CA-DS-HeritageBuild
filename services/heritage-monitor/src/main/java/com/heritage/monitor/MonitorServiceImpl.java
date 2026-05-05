package com.heritage.monitor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import io.grpc.stub.StreamObserver;

public class MonitorServiceImpl extends MonitorServiceGrpc.MonitorServiceImplBase{
    private final Map<String, String> siteRegistry = new ConcurrentHashMap<>();
    @Override
    public StreamObserver<StatusRequest> reportStatusBatch(StreamObserver<StatusSummary> responseObserver){
        return new StreamObserver<StatusRequest>(){
            private final List<String> statusLog = new ArrayList<>();
            private String lastStatusReceived = "None";

            @Override
            public void onNext(StatusRequest request){
                lastStatusReceived = request.getStatus();
                statusLog.add(request.getServiceName() + ": " + lastStatusReceived);
                System.out.println("Status Update received from: " + request.getServiceName());
            }

            @Override
            public void onError(Throwable t){
                System.err.println("Monitor Stream Error: " + t.getMessage());
            }

            @Override
            public void onCompleted(){
                StatusSummary summary = StatusSummary.newBuilder().setReportsReceived(statusLog.size()).setLastStatus(lastStatusReceived).build();

                responseObserver.onNext(summary);
                responseObserver.onCompleted();
                System.out.println("Batch processing complete. Summary sent.");
            }
        };
    }

    @Override
    public void registerSite(RegisterSiteRequest req, StreamObserver<RegisterSiteResponse> obs){
        System.out.println("Registering site: " + req.getName()+ " | " + req.getCountry());
        boolean duplicate = siteRegistry.values().stream().anyMatch(v -> v.equalsIgnoreCase(req.getName() + "|" + req.getCountry()));

        if (duplicate) {
            obs.onNext(RegisterSiteResponse.newBuilder().setSiteId("").setStatus("DUPLICATE").setMessage("Site '" + req.getName() + "' in " + req.getCountry() + " already registered.").build());
            obs.onCompleted();
            return;
        }

        String siteId = "SITE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        siteRegistry.put(siteId, req.getName() + "|" + req.getCountry());

        obs.onNext(RegisterSiteResponse.newBuilder().setSiteId(siteId).setStatus("REGISTERED").setMessage("Site '" + req.getName() + "' registered with ID: " + siteId).build());
        obs.onCompleted();
    }

    @Override
    public void streamRiskAlerts(RiskAlertSubscription req, StreamObserver<RiskAlert> obs){
        System.out.println("Streaming alerts, min severity: " + req.getMinSeverity());

        List<RiskAlert> alerts = Arrays.asList(
            buildAlert("FLOOD", "HIGH", "SITE-001", "Rising water levels near site boundary."),
            buildAlert("EROSION", "MEDIUM", "SITE-002", "Coastal erosion rate up 12% this month."),
            buildAlert("VANDALISM", "LOW", "SITE-001", "Graffiti on north perimeter wall."),
            buildAlert("POLLUTION", "HIGH","SITE-003", "Air quality index exceeded safe threshold."),
            buildAlert("FLOOD", "CRITICAL", "SITE-002", "URGENT: Flood defences breached.")
        );

        int minLevel = severityLevel(req.getMinSeverity());
        for (RiskAlert alert : alerts) {
            if(severityLevel(alert.getSeverity()) >= minLevel){
                obs.onNext(alert);
                try { Thread.sleep(600); } catch (InterruptedException ignored) {}
            }
        }
        obs.onCompleted();
    }

    @Override
    public StreamObserver<SensorReading> liveMonitorSession(
            StreamObserver<MonitoringUpdate> responseObserver){

        return new StreamObserver<SensorReading>() {

            @Override
            public void onNext(SensorReading r){
                System.out.println("Sensor: " + r.getSensorType() + " = " + r.getValue() + " " + r.getUnit());

                String assessment;
                switch (r.getSensorType().toUpperCase()) {
                    case "HUMIDITY":
                        assessment = r.getValue() > 85 ? "WARNING" : r.getValue() > 70 ? "WATCH" : "STABLE";
                        break;
                    case "VIBRATION":
                        assessment = r.getValue() > 5 ? "CRITICAL" : r.getValue() > 2 ? "WARNING" : "STABLE";
                        break;
                    case "AIR_QUALITY":
                        assessment = r.getValue() > 150 ? "CRITICAL" : r.getValue() > 100 ? "WARNING" : "STABLE";
                        break;
                    default:
                        assessment = "STABLE";
                }

                responseObserver.onNext(MonitoringUpdate.newBuilder().setSiteId(r.getSiteId()).setAssessment(assessment).setDetail(r.getSensorType() + " at " + r.getValue() + r.getUnit() + " — " + assessment).setTimestamp(Instant.now().toEpochMilli()).build());
            }

            @Override
            public void onError(Throwable t){
                System.err.println("Live session error: " + t.getMessage());
            }

            @Override
            public void onCompleted(){
                responseObserver.onCompleted();
            }
        };
    }

    private RiskAlert buildAlert(String type, String severity, String siteId, String msg){
        return RiskAlert.newBuilder().setRiskId("RISK-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase()).setSiteId(siteId).setSiteName(siteRegistry.getOrDefault(siteId, "Unknown Site")).setRiskType(type).setSeverity(severity).setMessage(msg).setTimestamp(Instant.now().toEpochMilli()).build();
    }

    private int severityLevel(String s){
        switch (s.toUpperCase()) {
            case "LOW": return 1;
            case "MEDIUM": return 2;
            case "HIGH": return 3;
            case "CRITICAL": return 4;
            default: return 0;
        }
    }
}