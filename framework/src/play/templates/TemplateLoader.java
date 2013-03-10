package play.templates;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.lang.StringUtils;

import play.Logger;
import play.Play;
import play.exceptions.TemplateCompilationException;
import play.exceptions.TemplateNotFoundException;
import play.mvc.Http;
import play.mvc.Http.Header;
import play.vfs.VirtualFile;

/**
 * Load templates
 */
public class TemplateLoader {

    protected static Map<String, BaseTemplate> templates = new HashMap<String, BaseTemplate>();
    /**
     * See getUniqueNumberForTemplateFile() for more info
     */
    private static AtomicLong nextUniqueNumber = new AtomicLong(1000);//we start on 1000
    private static Map<String, String> templateFile2UniqueNumber = new HashMap<String, String>();

    private static String mobilePrefix = Play.configuration.getProperty("deviceDetection.mobileSiteDirectoryPrefix");
    private static String fullPrefix = Play.configuration.getProperty("deviceDetection.fullSiteDirectoryPrefix");

    private static String[] uaKeywords = {};

    static {
        String keywords = (String) Play.configuration.get("deviceDetection.mobileUserAgentKeywords");
        if (StringUtils.isNotEmpty(keywords)) {
            uaKeywords = keywords.split("\\|");
        }
    }

    /**
     * All loaded templates is cached in the templates-list using a key.
     * This key is included as part of the classname for the generated class for a specific template.
     * The key is included in the classname to make it possible to resolve the original template-file
     * from the classname, when creating cleanStackTrace
     * <p/>
     * This method returns a unique representation of the path which is usable as part of a classname
     *
     * @param path
     * @return
     */
    public static String getUniqueNumberForTemplateFile(String path) {
        //a path cannot be a valid classname so we have to convert it somehow.
        //If we did some encoding on the path, the result would be at least as long as the path.
        //Therefor we assign a unique number to each path the first time we see it, and store it..
        //This way, all seen paths gets a unique number. This number is our UniqueValidClassnamePart..

        String uniqueNumber = templateFile2UniqueNumber.get(path);
        if (uniqueNumber == null) {
            //this is the first time we see this path - must assign a unique number to it.
            uniqueNumber = Long.toString(nextUniqueNumber.getAndIncrement());
            templateFile2UniqueNumber.put(path, uniqueNumber);
        }
        return uniqueNumber;
    }

    /**
     * Load a template from a virtual file
     *
     * @param file A VirtualFile
     * @return The executable template
     */
    public static Template load(VirtualFile file) {
        // Try with plugin
        Template pluginProvided = Play.pluginCollection.loadTemplate(file);
        if (pluginProvided != null) {
            return pluginProvided;
        }

        // Use default engine
        final String key = getUniqueNumberForTemplateFile(file.relativePath());
        if (!templates.containsKey(key) || templates.get(key).compiledTemplate == null) {
            if (Play.usePrecompiled) {
                BaseTemplate template = new GroovyTemplate(file.relativePath().replaceAll("\\{(.*)\\}", "from_$1").replace(":", "_").replace("..", "parent"), file.contentAsString());
                try {
                    template.loadPrecompiled();
                    templates.put(key, template);
                    return template;
                } catch (Exception e) {
                    Logger.warn("Precompiled template %s not found, trying to load it dynamically...", file.relativePath());
                }
            }
            BaseTemplate template = new GroovyTemplate(file.relativePath(), file.contentAsString());
            if (template.loadFromCache()) {
                templates.put(key, template);
            } else {
                templates.put(key, new GroovyTemplateCompiler().compile(file));
            }
        } else {
            BaseTemplate template = templates.get(key);
            if (Play.mode == Play.Mode.DEV && template.timestamp < file.lastModified()) {
                templates.put(key, new GroovyTemplateCompiler().compile(file));
            }
        }
        if (templates.get(key) == null) {
            throw new TemplateNotFoundException(file.relativePath());
        }
        return templates.get(key);
    }

    /**
     * Load a template from a String
     *
     * @param key    A unique identifier for the template, used for retreiving a cached template
     * @param source The template source
     * @return A Template
     */
    public static BaseTemplate load(String key, String source) {
        if (!templates.containsKey(key) || templates.get(key).compiledTemplate == null) {
            BaseTemplate template = new GroovyTemplate(key, source);
            if (template.loadFromCache()) {
                templates.put(key, template);
            } else {
                templates.put(key, new GroovyTemplateCompiler().compile(template));
            }
        } else {
            BaseTemplate template = new GroovyTemplate(key, source);
            if (Play.mode == Play.Mode.DEV) {
                templates.put(key, new GroovyTemplateCompiler().compile(template));
            }
        }
        if (templates.get(key) == null) {
            throw new TemplateNotFoundException(key);
        }
        return templates.get(key);
    }

    /**
     * Clean the cache for that key
     * Then load a template from a String
     *
     * @param key    A unique identifier for the template, used for retreiving a cached template
     * @param source The template source
     * @return A Template
     */
    public static BaseTemplate load(String key, String source, boolean reload) {
        cleanCompiledCache(key);
        return load(key, source);
    }

    /**
     * Load template from a String, but don't cache it
     *
     * @param source The template source
     * @return A Template
     */
    public static BaseTemplate loadString(String source) {
        BaseTemplate template = new GroovyTemplate(source);
        return new GroovyTemplateCompiler().compile(template);
    }

    /**
     * Cleans the cache for all templates
     */
    public static void cleanCompiledCache() {
        templates.clear();
    }

    /**
     * Cleans the specified key from the cache
     *
     * @param key The template key
     */
    public static void cleanCompiledCache(String key) {
        templates.remove(key);
    }

    /**
     * Load a template, checking for a multi-tenant template for looking for a default play app template
     *
     * @param path           The path of the template (ex: Application/index.html)
     * @param tenant         the tenant to look for the view in for multi-tenancy
     * @param mobileTemplate if the template to be loaded is for mobile devices
     * @return The executable template
     */
    public static Template loadMultiTenant(String path, String tenant, boolean mobileTemplate) {
        Template template = null;
        VirtualFile tf = null;

        //Hack for templates that supply a custom desktop, but not a custom mobile or vice-verse


        //try to load the mobile multi-tenant template with the given prefix or the domain of the current request
        if (mobileTemplate) {
            for (VirtualFile vf : Play.templatesPath) {
                if (vf == null) {
                    continue;
                }

                if (StringUtils.isNotEmpty(tenant)) {
                    tf = vf.child(tenant + File.separator + mobilePrefix + File.separator + path);
                    if (tf.exists()) {
                        template = TemplateLoader.load(tf);
                        break;
                    }
                }


                //try to load the mobile built-in template in the play project
                tf = vf.child(mobilePrefix + File.separator + path);
                if (tf.exists()) {
                    template = TemplateLoader.load(tf);
                    break;
                }
            }
        }

        if (template == null) {
            for (VirtualFile vf : Play.templatesPath) {
                if (vf == null) {
                    continue;
                }
                //try to load the full multi-tenant template with the given prefix or the domain of the current request
                if (StringUtils.isNotEmpty(tenant)) {
                    tf = vf.child(tenant + File.separator + fullPrefix + File.separator + path);
                    if (tf.exists()) {
                        template = TemplateLoader.load(tf);
                        break;
                    }
                }

                //try to load the full built-in template in the play project
                tf = vf.child(fullPrefix + File.separator + path);
                if (tf.exists()) {
                    template = TemplateLoader.load(tf);
                    break;
                }

                tf = vf.child(path);
                if (tf.exists()) {
                    template = TemplateLoader.load(tf);
                    break;
                }

            }
        }

        /*
        if (template == null) {
        //When using the old 'key = (file.relativePath().hashCode() + "").replace("-", "M");',
        //the next line never return anything, since all values written to templates is using the
        //above key.
        //when using just file.relativePath() as key, the next line start returning stuff..
        //therefor I have commented it out.
        template = templates.get(path);
        }
         */
        //TODO: remove ?
        if (template == null) {
            tf = Play.getVirtualFile(path);
            if (tf != null && tf.exists()) {
                template = TemplateLoader.load(tf);
            } else {
                throw new TemplateNotFoundException(path);
            }
        }
        return template;
    }

    /**
     * Load a template
     *
     * @param path The path of the template (ex: Application/index.html)
     * @return The executable template
     */
    public static Template load(String path) {
        Http.Request currentRequest = Http.Request.current();
        if (currentRequest != null) {
            boolean isMobile = false;
            Header userAgent = currentRequest.headers.get("user-agent");
            if (userAgent != null) {
                String ua = userAgent.value().toLowerCase();
                for (String keyword : uaKeywords) {
                    if (ua.contains(keyword)) {
                        isMobile = true;
                        break;
                    }
                }
            }
            return loadMultiTenant(path, currentRequest.domain, isMobile);
        }
        return loadMultiTenant(path, null, false);
    }


    /**
     * List all found templates
     *
     * @return A list of executable templates
     */
    public static List<Template> getAllTemplate() {
        List<Template> res = new ArrayList<Template>();
        for (VirtualFile virtualFile : Play.templatesPath) {
            scan(res, virtualFile);
        }
        for (VirtualFile root : Play.roots) {
            VirtualFile vf = root.child("conf/routes");
            if (vf != null && vf.exists()) {
                Template template = load(vf);
                if (template != null) {
                    template.compile();
                }
            }
        }
        return res;
    }

    private static void scan(List<Template> templates, VirtualFile current) {
        if (!current.isDirectory() && !current.getName().startsWith(".") && !current.getName().endsWith(".scala.html")) {
            long start = System.currentTimeMillis();
            Template template = load(current);
            if (template != null) {
                try {
                    template.compile();
                    if (Logger.isTraceEnabled()) {
                        Logger.trace("%sms to load %s", System.currentTimeMillis() - start, current.getName());
                    }
                } catch (TemplateCompilationException e) {
                    Logger.error("Template %s does not compile at line %d", e.getTemplate().name, e.getLineNumber());
                    throw e;
                }
                templates.add(template);
            }
        } else if (current.isDirectory() && !current.getName().startsWith(".")) {
            for (VirtualFile virtualFile : current.list()) {
                scan(templates, virtualFile);
            }
        }
    }
}