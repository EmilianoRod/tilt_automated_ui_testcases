package Utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class TeamCsvFixtureFactory {




    private TeamCsvFixtureFactory() {}

    /**
     * Build a temporary CSV with N recipients and return both the path and the emails.
     * NOTE: Adjust the header to match the real template.
     */
    public static TeamCsvFixture buildTeamCsvWithNRecipients(String tag, int n) throws IOException {
        Path csv = Files.createTempFile("team-upload-" + tag + "-", ".csv");

        String header = "First Name,Last Name,Email\n"; // tweak if needed
        StringBuilder sb = new StringBuilder(header);

        List<String> emails = new ArrayList<>();

        for (int i = 1; i <= n; i++) {
            String local = tag + "-" + i;
            String first = "FN_" + local;
            String last  = "LN_" + local;
            String email = local + "@example.test";

            sb.append(first).append(",")
                    .append(last).append(",")
                    .append(email).append("\n");

            emails.add(email);
        }

        Files.writeString(csv, sb.toString(), StandardCharsets.UTF_8);
        System.out.println("[TeamCsvFixture] CSV created at: " + csv);

        return new TeamCsvFixture(csv, emails);
    }

    public static final class TeamCsvFixture {
        private final Path path;
        private final List<String> emails;

        public TeamCsvFixture(Path path, List<String> emails) {
            this.path = path;
            this.emails = emails;
        }

        public Path getPath() {
            return path;
        }

        public List<String> getEmails() {
            return emails;
        }
    }



}
