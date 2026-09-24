package ph.dragonview.mobile.data.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.junit.Test;

import java.util.Collections;

import ph.dragonview.mobile.data.model.CompleteSaleRequest;
import ph.dragonview.mobile.data.model.HarvestRequest;

public final class RequestContractTest {
    private final Gson gson = new Gson();

    @Test
    public void harvestPayloadMatchesRpcContract() {
        HarvestRequest request = new HarvestRequest("B-001", "2026-09-08",
                Collections.singletonList(new HarvestRequest.Item("LARGE", "A", 12)));
        JsonObject json = gson.toJsonTree(request).getAsJsonObject();
        assertEquals("B-001", json.get("batchNumber").getAsString());
        assertEquals("2026-09-08", json.get("harvestDate").getAsString());
        assertEquals(12, json.getAsJsonArray("items").get(0).getAsJsonObject().get("pieces").getAsInt());
    }

    @Test
    public void salePayloadKeepsNestedCustomerAndPaymentContract() {
        CompleteSaleRequest.Item item = new CompleteSaleRequest.Item("LARGE", "A", 2, 1.5);
        CompleteSaleRequest request = new CompleteSaleRequest("Buyer", "Farm road", "0917", "buyer@example.com",
                Collections.singletonList(item), "CASH", "500", null, null);
        JsonObject json = gson.toJsonTree(request).getAsJsonObject();
        assertTrue(json.get("customer").isJsonObject());
        assertTrue(json.get("payment").isJsonObject());
        assertEquals("SELECTED_ITEMS", json.get("saleMode").getAsString());
    }
}

