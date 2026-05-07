package com.heritage.client;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;

import com.heritage.grant.CountryRequest;
import com.heritage.grant.DocumentChunk;
import com.heritage.grant.GrantRequest;
import com.heritage.grant.GrantResponse;
import com.heritage.grant.GrantServiceGrpc;
import com.heritage.grant.TrackRequest;
import com.heritage.grant.UploadSummary;
import com.heritage.material.Empty;
import com.heritage.material.ItemRequest;
import com.heritage.material.ItemResponse;
import com.heritage.material.SearchQuery;
import com.heritage.material.SubmitRequest;
import com.heritage.material.SubmitSummary;
import com.heritage.material.VaultServiceGrpc;
import com.heritage.monitor.AuthInterceptor;
import com.heritage.monitor.MonitorServiceGrpc;
import com.heritage.monitor.RegisterSiteRequest;
import com.heritage.monitor.RegisterSiteResponse;
import com.heritage.monitor.RiskAlert;
import com.heritage.monitor.RiskAlertSubscription;
import com.heritage.monitor.StatusRequest;
import com.heritage.monitor.StatusSummary;
import com.heritage.naming.DiscoverRequest;
import com.heritage.naming.DiscoverResponse;
import com.heritage.naming.NamingServiceGrpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;

public class ClientApp extends JFrame {

    private static final String AUTH_TOKEN = "heritage-secret-2025";

    private final JTextArea outputArea = new JTextArea();
    private ManagedChannel namingChannel;
    private NamingServiceGrpc.NamingServiceBlockingStub namingStub;

    public ClientApp() {
        super("HeritageBuild Client");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 700);
        setLayout(new BorderLayout(10, 10));

        namingChannel = ManagedChannelBuilder.forAddress("localhost", 50051).usePlaintext().build();
        namingStub = NamingServiceGrpc.newBlockingStub(namingChannel);

        outputArea.setEditable(false);
        outputArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(outputArea);
        scroll.setBorder(new TitledBorder("Output"));
        add(scroll, BorderLayout.CENTER);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Heritage Monitor", buildMonitorPanel());
        tabs.addTab("Material Vault", buildMaterialPanel());
        tabs.addTab("Grant Coordinator", buildGrantPanel());
        add(tabs, BorderLayout.NORTH);

        setVisible(true);
    }

    private void log(String msg){
        SwingUtilities.invokeLater(() -> outputArea.append(msg + "\n"));
    }

    private ManagedChannel discoverAndConnect(String serviceName) {
        try {
            DiscoverResponse resp = namingStub.discoverService(
                DiscoverRequest.newBuilder().setServiceName(serviceName).build());
            if(resp.getAddress().isEmpty()){
                log("Service not found: " + serviceName);
                return null;
            }
            log("Discovery: " + serviceName + " found at " + resp.getAddress() + ":" + resp.getPort());
            return ManagedChannelBuilder.forAddress(resp.getAddress(), resp.getPort()).usePlaintext().build();
        } catch (Exception e) {
            log("Could not discover " + serviceName + ": " + e.getMessage());
            return null;
        }
    }

    // Builds metadata headers with auth token
    private Metadata authHeaders() {
        Metadata headers = new Metadata();
        headers.put(AuthInterceptor.TOKEN_KEY, AUTH_TOKEN);
        return headers;
    }

    private JPanel buildMonitorPanel() {
        JPanel panel = new JPanel(new GridLayout(0, 2, 5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JTextField siteName = new JTextField("Skellig Michael");
        JTextField siteCountry = new JTextField("Ireland");
        JTextField siteType = new JTextField("UNESCO");
        JButton regBtn = new JButton("Register Site");
        regBtn.addActionListener(e -> new Thread(() -> {
            ManagedChannel ch = discoverAndConnect("Heritage-Monitor");
            if (ch == null) return;
            try {
                MonitorServiceGrpc.MonitorServiceBlockingStub stub = MonitorServiceGrpc.newBlockingStub(ch).withInterceptors(MetadataUtils.newAttachHeadersInterceptor(authHeaders())).withDeadlineAfter(5, TimeUnit.SECONDS);
                RegisterSiteResponse resp = stub.registerSite(RegisterSiteRequest.newBuilder().setName(siteName.getText()).setCountry(siteCountry.getText()).setType(siteType.getText()).build());
                log("RegisterSite → " + resp.getStatus() + " | " + resp.getMessage());
            } catch (StatusRuntimeException ex) {
                log("RegisterSite error: " + ex.getStatus());
            } finally { ch.shutdown(); }
        }).start());
        panel.add(labelledField("Site Name:", siteName));
        panel.add(labelledField("Country:", siteCountry));
        panel.add(labelledField("Type:", siteType));
        panel.add(regBtn);

        JTextField minSev = new JTextField("LOW");
        JButton alertBtn = new JButton("Stream Risk Alerts");
        alertBtn.addActionListener(e -> new Thread(() -> {
            ManagedChannel ch = discoverAndConnect("Heritage-Monitor");
            if (ch == null) return;
            try {
                MonitorServiceGrpc.MonitorServiceBlockingStub stub = MonitorServiceGrpc.newBlockingStub(ch).withInterceptors(MetadataUtils.newAttachHeadersInterceptor(authHeaders())).withDeadlineAfter(10, TimeUnit.SECONDS);
                Iterator<RiskAlert> alerts = stub.streamRiskAlerts(RiskAlertSubscription.newBuilder().setMinSeverity(minSev.getText()).build());
                while (alerts.hasNext()) {
                    RiskAlert a = alerts.next();
                    log("ALERT [" + a.getSeverity() + "] " + a.getRiskType() + " — " + a.getMessage());
                }
            } catch (StatusRuntimeException ex) {
                log("Stream error: " + ex.getStatus());
            } finally { ch.shutdown(); }
        }).start());
        panel.add(labelledField("Min Severity:", minSev));
        panel.add(alertBtn);

        JTextField batchSite = new JTextField("SITE-001");
        JTextField batchStatus = new JTextField("STABLE");
        JButton batchBtn = new JButton("Send Status Batch");
        batchBtn.addActionListener(e -> new Thread(() -> {
            ManagedChannel ch = discoverAndConnect("Heritage-Monitor");
            if (ch == null) return;
            try {
                MonitorServiceGrpc.MonitorServiceStub asyncStub = MonitorServiceGrpc.newStub(ch).withInterceptors(MetadataUtils.newAttachHeadersInterceptor(authHeaders())).withDeadlineAfter(5, TimeUnit.SECONDS);
                StreamObserver<StatusSummary> responseObserver = new StreamObserver<StatusSummary>() {
                    public void onNext(StatusSummary s) {
                        log("Batch Summary → received=" + s.getReportsReceived() + " lastStatus=" + s.getLastStatus());
                    }
                    public void onError(Throwable t) { log("Batch error: " + t.getMessage()); }
                    public void onCompleted() { log("Batch complete."); }
                };
                StreamObserver<StatusRequest> requestObserver = asyncStub.reportStatusBatch(responseObserver);
                for (int i = 1; i <= 3; i++) {
                    requestObserver.onNext(StatusRequest.newBuilder()
                            .setServiceName(batchSite.getText() + "-sensor-" + i)
                            .setStatus(batchStatus.getText()).build());
                }
                requestObserver.onCompleted();
            } finally {
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                ch.shutdown();
            }
        }).start());
        panel.add(labelledField("Service Name:", batchSite));
        panel.add(labelledField("Status:", batchStatus));
        panel.add(batchBtn);

        return panel;
    }

    private JPanel buildMaterialPanel() {
        JPanel panel = new JPanel(new GridLayout(0, 2, 5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JTextField itemId = new JTextField("H001");
        JButton getBtn = new JButton("Get Item");
        getBtn.addActionListener(e -> new Thread(() -> {
            ManagedChannel ch = discoverAndConnect("Material-Vault");
            if (ch == null) return;
            try {
                VaultServiceGrpc.VaultServiceBlockingStub stub = VaultServiceGrpc.newBlockingStub(ch).withInterceptors(MetadataUtils.newAttachHeadersInterceptor(authHeaders())).withDeadlineAfter(5, TimeUnit.SECONDS);
                ItemResponse resp = stub.getItem(ItemRequest.newBuilder().setItemId(itemId.getText()).build());
                log("GetItem → " + resp.getName() + " | " + resp.getDescription());
            } catch (StatusRuntimeException ex) {
                log("GetItem error: " + ex.getStatus());
            } finally { ch.shutdown(); }
        }).start());
        panel.add(labelledField("Item ID:", itemId));
        panel.add(getBtn);

        JButton listBtn = new JButton("List All Items");
        listBtn.addActionListener(e -> new Thread(() -> {
            ManagedChannel ch = discoverAndConnect("Material-Vault");
            if (ch == null) return;
            try {
                VaultServiceGrpc.VaultServiceBlockingStub stub = VaultServiceGrpc.newBlockingStub(ch).withInterceptors(MetadataUtils.newAttachHeadersInterceptor(authHeaders())).withDeadlineAfter(5, TimeUnit.SECONDS);
                Iterator<ItemResponse> items = stub.listAllItems(Empty.newBuilder().build());
                while (items.hasNext()) {
                    ItemResponse item = items.next();
                    log("Material: " + item.getName() + " | " + item.getDescription());
                }
            } catch (StatusRuntimeException ex) {
                log("ListAll error: " + ex.getStatus());
            } finally { ch.shutdown(); }
        }).start());
        panel.add(new JLabel());
        panel.add(listBtn);

        JTextField matName = new JTextField("Lime Mortar");
        JTextField matRegion = new JTextField("Europe");
        JButton submitBtn = new JButton("Submit Material");
        submitBtn.addActionListener(e -> new Thread(() -> {
            ManagedChannel ch = discoverAndConnect("Material-Vault");
            if (ch == null) return;
            try {
                VaultServiceGrpc.VaultServiceStub asyncStub = VaultServiceGrpc.newStub(ch).withInterceptors(MetadataUtils.newAttachHeadersInterceptor(authHeaders())).withDeadlineAfter(5, TimeUnit.SECONDS);
                StreamObserver<SubmitSummary> responseObserver = new StreamObserver<SubmitSummary>() {
                    public void onNext(SubmitSummary s) {
                        log("Submit → received=" + s.getTotalReceived()
                                + " accepted=" + s.getTotalAccepted() + " | " + s.getMessage());
                    }
                    public void onError(Throwable t) { log("Submit error: " + t.getMessage()); }
                    public void onCompleted() { log("Submit complete."); }
                };
                StreamObserver<SubmitRequest> requestObserver = asyncStub.submitMaterials(responseObserver);
                requestObserver.onNext(SubmitRequest.newBuilder().setName(matName.getText())
                        .setRegion(matRegion.getText()).setSubmittedBy("ClientApp").build());
                requestObserver.onCompleted();
            } finally {
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                ch.shutdown();
            }
        }).start());
        panel.add(labelledField("Material Name:", matName));
        panel.add(labelledField("Region:", matRegion));
        panel.add(submitBtn);

        JTextField searchKw = new JTextField("Kells");
        JButton searchBtn = new JButton("Search Materials");
        searchBtn.addActionListener(e -> new Thread(() -> {
            ManagedChannel ch = discoverAndConnect("Material-Vault");
            if (ch == null) return;
            try {
                VaultServiceGrpc.VaultServiceStub asyncStub = VaultServiceGrpc.newStub(ch).withInterceptors(MetadataUtils.newAttachHeadersInterceptor(authHeaders())).withDeadlineAfter(5, TimeUnit.SECONDS);
                StreamObserver<ItemResponse> responseObserver = new StreamObserver<ItemResponse>() {
                    public void onNext(ItemResponse r) { log("Search result → " + r.getName()); }
                    public void onError(Throwable t) { log("Search error: " + t.getMessage()); }
                    public void onCompleted() { log("Search complete."); }
                };
                StreamObserver<SearchQuery> requestObserver = asyncStub.searchSession(responseObserver);
                requestObserver.onNext(SearchQuery.newBuilder().setKeyword(searchKw.getText()).build());
                requestObserver.onCompleted();
            } finally {
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                ch.shutdown();
            }
        }).start());
        panel.add(labelledField("Keyword:", searchKw));
        panel.add(searchBtn);

        return panel;
    }

    private JPanel buildGrantPanel() {
        JPanel panel = new JPanel(new GridLayout(0, 2, 5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JTextField grantTitle = new JTextField("Heritage Restoration");
        JTextField grantAmount = new JTextField("250000");
        JButton negotiateBtn = new JButton("Negotiate Grant");
        negotiateBtn.addActionListener(e -> new Thread(() -> {
            ManagedChannel ch = discoverAndConnect("Grant-Coordinator");
            if (ch == null) return;
            try {
                GrantServiceGrpc.GrantServiceStub asyncStub = GrantServiceGrpc.newStub(ch)
                        .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(authHeaders()))
                        .withDeadlineAfter(5, TimeUnit.SECONDS);
                StreamObserver<GrantResponse> responseObserver = new StreamObserver<GrantResponse>() {
                    public void onNext(GrantResponse r) {
                        log("Negotiate → " + r.getStatus() + " | " + r.getFeedback());
                    }
                    public void onError(Throwable t) { log("Negotiate error: " + t.getMessage()); }
                    public void onCompleted() { log("Negotiation complete."); }
                };
                StreamObserver<GrantRequest> requestObserver = asyncStub.negotiateGrant(responseObserver);
                requestObserver.onNext(GrantRequest.newBuilder()
                        .setProjectTitle(grantTitle.getText())
                        .setAmountRequested(Double.parseDouble(grantAmount.getText())).build());
                requestObserver.onCompleted();
            } finally {
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                ch.shutdown();
            }
        }).start());
        panel.add(labelledField("Project Title:", grantTitle));
        panel.add(labelledField("Amount:", grantAmount));
        panel.add(negotiateBtn);

        JTextField appId = new JTextField("GRT-X");
        JButton statusBtn = new JButton("Get App Status");
        statusBtn.addActionListener(e -> new Thread(() -> {
            ManagedChannel ch = discoverAndConnect("Grant-Coordinator");
            if (ch == null) return;
            try {
                GrantServiceGrpc.GrantServiceBlockingStub stub = GrantServiceGrpc.newBlockingStub(ch).withInterceptors(MetadataUtils.newAttachHeadersInterceptor(authHeaders())).withDeadlineAfter(5, TimeUnit.SECONDS);
                GrantResponse resp = stub.getApplicationStatus(
                        TrackRequest.newBuilder().setApplicationId(appId.getText()).build());
                log("Status → " + resp.getStatus() + " | " + resp.getFeedback());
            } catch (StatusRuntimeException ex) {
                log("Status error: " + ex.getStatus());
            } finally { ch.shutdown(); }
        }).start());
        panel.add(labelledField("Application ID:", appId));
        panel.add(statusBtn);

        JTextField country = new JTextField("ireland");
        JButton listGrantsBtn = new JButton("List Available Grants");
        listGrantsBtn.addActionListener(e -> new Thread(() -> {
            ManagedChannel ch = discoverAndConnect("Grant-Coordinator");
            if(ch == null) return;
            try{
                GrantServiceGrpc.GrantServiceBlockingStub stub = GrantServiceGrpc.newBlockingStub(ch).withInterceptors(MetadataUtils.newAttachHeadersInterceptor(authHeaders())).withDeadlineAfter(5, TimeUnit.SECONDS);
                Iterator<GrantResponse> grants = stub.listAvailableGrants(
                        CountryRequest.newBuilder().setCountry(country.getText()).build());
                while (grants.hasNext()) {
                    GrantResponse g = grants.next();
                    log("Available → " + g.getStatus() + " | " + g.getFeedback());
                }
            } catch (StatusRuntimeException ex) {
                log("ListGrants error: " + ex.getStatus());
            } finally { ch.shutdown(); }
        }).start());
        panel.add(labelledField("Country:", country));
        panel.add(listGrantsBtn);

        JTextField docAppId = new JTextField("GRT-XXXXXXXX");
        JButton uploadBtn = new JButton("Upload Document");
        uploadBtn.addActionListener(e -> new Thread(() -> {
            ManagedChannel ch = discoverAndConnect("Grant-Coordinator");
            if (ch == null) return;
            try {
                GrantServiceGrpc.GrantServiceStub asyncStub = GrantServiceGrpc.newStub(ch).withInterceptors(MetadataUtils.newAttachHeadersInterceptor(authHeaders())).withDeadlineAfter(5, TimeUnit.SECONDS);
                StreamObserver<UploadSummary> responseObserver = new StreamObserver<UploadSummary>(){
                    public void onNext(UploadSummary s) {
                        log("Upload → success=" + s.getSuccess() + " | " + s.getMessage());
                    }
                    public void onError(Throwable t) { log("Upload error: " + t.getMessage()); }
                    public void onCompleted() { log("Upload complete."); }
                };
                StreamObserver<DocumentChunk> requestObserver = asyncStub.uploadDocuments(responseObserver);
                byte[] fakeData = "Sample document content".getBytes();
                requestObserver.onNext(DocumentChunk.newBuilder().setApplicationId(docAppId.getText()).setFilename("proposal.pdf").setData(com.google.protobuf.ByteString.copyFrom(fakeData)).setChunkIndex(0).setIsLastChunk(true).build());
                requestObserver.onCompleted();
            } finally {
                try{ Thread.sleep(1000); } catch (InterruptedException ignored){}
                ch.shutdown();
            }
        }).start());
        panel.add(labelledField("App ID for Upload:", docAppId));
        panel.add(uploadBtn);

        return panel;
    }

    private JPanel labelledField(String label, JTextField field){
        JPanel p = new JPanel(new BorderLayout(5, 0));
        p.add(new JLabel(label), BorderLayout.WEST);
        p.add(field, BorderLayout.CENTER);
        return p;
    }

    public static void main(String[] args){
        SwingUtilities.invokeLater(ClientApp::new);
    }
}