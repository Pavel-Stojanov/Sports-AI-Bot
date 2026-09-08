package mk.ukim.finki.aibotbackend.integration.vezilka;

import java.util.List;

/** Batch capability for the Public Donation API, preserving the template contract. */
public interface BatchVezilkaClient extends VezilkaClient {
    int MAX_ITEMS_PER_REQUEST = 100;

    DonationResponse submitTextDonations(List<TextDonationItem> items);
}
