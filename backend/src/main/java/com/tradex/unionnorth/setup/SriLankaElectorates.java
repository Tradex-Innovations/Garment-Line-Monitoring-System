package com.tradex.unionnorth.setup;

import static java.util.Map.entry;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Polling divisions, grouped by the registration form's administrative district. */
final class SriLankaElectorates {
    private SriLankaElectorates() {}

    // Election Commission polling-division names, verified 2026-09-14:
    // https://elections.gov.lk/en/voters/voters_statistics_E.html
    // Kilinochchi is in the Jaffna electoral district; Mannar, Mullaitivu and
    // Vavuniya form Vanni. Keep them separate for the 25-district address field.
    static final Map<String, List<String>> BY_DISTRICT = Map.ofEntries(
            entry("Ampara", names("Ampara", "Samanturai", "Kalmunai", "Potuvil")),
            entry("Anuradhapura", names("Medawachchiya", "Horowpothana", "Anuradhapura East",
                    "Anuradhapura West", "Kalawewa", "Mihintale", "Kekirawa")),
            entry("Badulla", names("Mahiyanganaya", "Wiyaluwa", "Passara", "Badulla", "Hali-Ela",
                    "Uva Paranagama", "Welimada", "Bandarawela", "Haputale")),
            entry("Batticaloa", names("Kalkudah", "Batticaloa", "Padiruppe")),
            entry("Colombo", names("Colombo North", "Colombo Central", "Borella", "Colombo East",
                    "Colombo West", "Dehiwala", "Ratmalana", "Kolonnawa", "Kotte", "Kaduwela",
                    "Avissawella", "Homagama", "Maharagama", "Kesbewa", "Moratuwa")),
            entry("Galle", names("Balapitiya", "Ambalangoda", "Karandeniya", "Bentara-Elpitiya",
                    "Hiniduma", "Baddegama", "Ratgama", "Galle", "Akmeemana", "Habaraduwa")),
            entry("Gampaha", names("Wattala", "Negombo", "Katana", "Divulapitiya", "Mirigama",
                    "Minuwangoda", "Attanagalla", "Gampaha", "Ja-Ela", "Mahara", "Dompe",
                    "Biyagama", "Kelaniya")),
            entry("Hambantota", names("Mulkirigala", "Beliatta", "Tangalle", "Tissamaharamaya")),
            entry("Jaffna", names("Kayts", "Waddukkoddai", "Kankasanturai", "Manipai", "Kopai",
                    "Uduppidi", "Point Pedro", "Chawakachcheri", "Nallur", "Jaffna")),
            entry("Kalutara", names("Panadura", "Bandaragama", "Horana", "Bulathsinhala",
                    "Matugama", "Kalutara", "Beruwala", "Agalawatta")),
            entry("Kandy", names("Galagedara", "Harispattuwa", "Pathadumbara", "Udadumbara",
                    "Teldeniya", "Kundasale", "Hewaheta", "Senkadagala", "Kandy", "Yatinuwara",
                    "Udanuwara", "Gampola", "Nawalapitiya")),
            entry("Kegalle", names("Dedigama", "Galigamuwa", "Kegalle", "Rambukkana", "Mawanella",
                    "Aranayake", "Yatiyantota", "Ruwanwella", "Deraniyagala")),
            entry("Kilinochchi", names("Kilinochchi")),
            entry("Kurunegala", names("Galgamuwa", "Nikaweratiya", "Yapahuwa", "Hiriyala",
                    "Wariyapola", "Panduwasnuwara", "Bingiriya", "Katugampola", "Kuliyapitiya",
                    "Dambadeniya", "Polgahawela", "Kurunegala", "Mawathagama", "Dodangaslanda")),
            entry("Mannar", names("Mannar")),
            entry("Matale", names("Dambulla", "Laggala", "Matale", "Rattota")),
            entry("Matara", names("Deniyaya", "Hakmana", "Akuressa", "Kamburupitiya", "Devinuwara",
                    "Matara", "Weligama")),
            entry("Monaragala", names("Bibila", "Monaragala", "Wellawaya")),
            entry("Mullaitivu", names("Mullaitivu")),
            entry("Nuwara Eliya", names("Nuwara Eliya", "Kotmale", "Hanguranketha", "Walapane")),
            entry("Polonnaruwa", names("Minneriya", "Medirigiriya", "Polonnaruwa")),
            entry("Puttalam", names("Puttalam", "Anamaduwa", "Chillaw", "Nattandiya", "Wennappuwa")),
            entry("Ratnapura", names("Eheliyagoda", "Ratnapura", "Pelmadulla", "Balangoda",
                    "Rakwana", "Nivithigala", "Kalawana", "Kolonna")),
            entry("Trincomalee", names("Seruwila", "Trincomalee", "Mutur")),
            entry("Vavuniya", names("Vavuniya")));

    static final List<String> ALL = BY_DISTRICT.values().stream()
            .flatMap(List::stream).sorted().toList();

    private static List<String> names(String... values) {
        return Arrays.stream(values).sorted().toList();
    }
}
