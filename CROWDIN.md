We use Crowdin to manage our translations.

You can use either the Android Studio Crowdin plugin or the command line tool.

**⚠️ IMPORTANT: All Crowdin uploads should be MANUAL ONLY**
- Never enable auto-upload features
- Always review changes before uploading to Crowdin
- Use `crowdin upload sources` command explicitly when ready to sync

# Android Studio Plugin

To get it working you need a file named `crowdin.properties` at the root of the repository.

**REQUIRED settings:**
```
project-id=456796
api-token=<secret-api-token>
auto-upload=false
disable-branches=true
```

**⚠️ CRITICAL:** Always set `auto-upload=false` to prevent automatic uploads!

**Note:** `crowdin.properties` is in `.gitignore` to prevent accidental commits.

# Command line 

It needs a configuration file named `crowdin.yml` at the root of the repository.

## API Token Configuration

The Crowdin CLI uses the `CROWDIN_API_TOKEN` environment variable for authentication, as specified in `crowdin.yml`:
```yaml
"api_token_env": "CROWDIN_API_TOKEN"
```
## Common Commands

### Upload Sources
```bash
crowdin upload sources
```
Uploads the source `strings.xml` file to Crowdin.

### Download Translations
```bash
crowdin download
```
Downloads all translations from Crowdin to your local project.

### Force Upload Translations (Override Pending Changes)
```bash
crowdin upload translations --auto-approve-imported --import-eq-suggestions
```
Force uploads all local translations to Crowdin, overriding any pending changes made by translators.

**Options:**
- `--auto-approve-imported`: Automatically approves the uploaded translations, overriding pending translator changes
- `--import-eq-suggestions`: Includes translations even when they match the source string

**Use case:** When you need to revert bad translations or push corrected versions from your local repository to override pending changes on Crowdin.

**Warning:** This will override any pending work by translators on Crowdin. Use with caution and communicate with your translation team before using this command.
