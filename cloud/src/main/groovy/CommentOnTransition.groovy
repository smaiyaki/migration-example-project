import com.adaptavist.hapi.cloud.jira.issues.Issues
import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

def eventIssue = Issues.getByKey(issue.key as String)
def now = ZonedDateTime.now(ZoneId.of("UTC")).format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm z"))

eventIssue.addComment("Issue moved to Implementing on ${now}")