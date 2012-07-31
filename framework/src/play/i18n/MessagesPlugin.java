package play.i18n;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang.StringUtils;

import play.Logger;
import play.Play;
import play.PlayPlugin;
import play.libs.IO;
import play.vfs.VirtualFile;

/**
 * Messages plugin
 */
public class MessagesPlugin extends PlayPlugin {

    static Long lastLoading = 0L;

    @Override
    public void onApplicationStart() {
        Messages.defaults = new Properties();
        try {
            FileInputStream is = new FileInputStream(new File(Play.frameworkPath, "resources/messages"));
            Messages.defaults.putAll(IO.readUtf8Properties(is));
        } catch(Exception e) {
            Logger.warn("Defaults messsages file missing");
        }
        for(VirtualFile module : Play.modules.values()) {
            VirtualFile messages = module.child("conf/messages");
            if(messages != null && messages.exists()) {
                Messages.defaults.putAll(read(messages)); 
            }
        }
        VirtualFile appDM = Play.getVirtualFile("conf/messages");
        if(appDM != null && appDM.exists()) {
            Messages.defaults.putAll(read(appDM));
        }
        for (String locale : Play.langs) {
            Properties properties = new Properties();
            for(VirtualFile module : Play.modules.values()) {
                VirtualFile messages = module.child("conf/messages." + locale);
                if(messages != null && messages.exists()) {
                    properties.putAll(read(messages)); 
                }
            }
            VirtualFile appM = Play.getVirtualFile("conf/messages." + locale);
            if(appM != null && appM.exists()) {
                properties.putAll(read(appM));
            } else {
                Logger.warn("Messages file missing for locale %s", locale);
            }     
            Messages.locales.put(locale, properties);
        }
        
        String multiTenantMessagesRoot = (String) Play.configuration.get("multiTenant.messagesRootDirectory");
        if (StringUtils.isNotEmpty(multiTenantMessagesRoot)) {
        	VirtualFile messageRoot = VirtualFile.open(new File(multiTenantMessagesRoot));
        	if (messageRoot.isDirectory()) {
        		for (VirtualFile child : messageRoot.list()) {
					if (!child.isDirectory()) {
						continue;
					}
					String name = child.getName();
					VirtualFile defaultMessages = child.child("messages");
					if (defaultMessages != null && defaultMessages.exists()) {
						Properties properties = new Properties();
						properties.putAll(read(defaultMessages));
		                Messages.multiTenantDefaults.put(name, properties); 
		            }
					Map<String, Properties> localeMessages = null;
					for (String locale : Play.langs) {
						if (localeMessages == null) {
							localeMessages = new HashMap<String, Properties>();
						}
			            Properties properties = new Properties();
		                VirtualFile messages = child.child("messages." + locale);
		                if (messages != null && messages.exists()) {
		                    properties.putAll(read(messages)); 
		                }
		                localeMessages.put(locale, properties);
					}
					if (localeMessages != null) {
						Messages.multiTenantLocales.put(name, localeMessages);
					}
				}
        	}
        }
        
        lastLoading = System.currentTimeMillis();
    }

    static Properties read(VirtualFile vf) {
        if (vf != null) {
            return IO.readUtf8Properties(vf.inputstream());
        }
        return null;
    }

    @Override
    public void detectChange() {
        if (Play.getVirtualFile("conf/messages")!=null && Play.getVirtualFile("conf/messages").lastModified() > lastLoading) {
            onApplicationStart();
            return;
        }
        for(VirtualFile module : Play.modules.values()) {
            if(module.child("conf/messages") != null && module.child("conf/messages").exists() && module.child("conf/messages").lastModified() > lastLoading) {
                onApplicationStart();
                return;
            }
        }
        for (String locale : Play.langs) {
            if (Play.getVirtualFile("conf/messages." + locale)!=null && Play.getVirtualFile("conf/messages." + locale).lastModified() > lastLoading) {
                onApplicationStart();
                return;
            }
            for(VirtualFile module : Play.modules.values()) {
                if(module.child("conf/messages."+locale) != null && module.child("conf/messages."+locale).exists() && module.child("conf/messages."+locale).lastModified() > lastLoading) {
                    onApplicationStart();
                    return;
                }
            }
        }
        String multiTenantMessagesRoot = (String) Play.configuration.get("multiTenant.messagesRootDirectory");
        if (StringUtils.isNotEmpty(multiTenantMessagesRoot)) {
        	VirtualFile messageRoot = VirtualFile.open(new File(multiTenantMessagesRoot));
        	if (messageRoot.isDirectory()) {
        		for (VirtualFile child : messageRoot.list()) {
					if (!child.isDirectory()) {
						continue;
					}
					VirtualFile defaultMessages = child.child("messages");
					if (defaultMessages != null && defaultMessages.exists() && defaultMessages.lastModified() > lastLoading) {
						onApplicationStart();
	                    return;
					}
					for (String locale : Play.langs) {
		                VirtualFile messages = child.child("messages." + locale);
		                	if (messages.lastModified() > lastLoading) {
							onApplicationStart();
		                    return;
		                }
					}
				}
        	}
        }
    }
}
