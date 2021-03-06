package opstrings

deployment(name:'Events Example') {
    groups('rio')

    resources(id: 'impl.jars', 'events/lib/hello-event.jar')

    service(name: 'Hello World') {
        interfaces {
            classes('events.Hello')
            resources('events/lib/hello-event-dl.jar')
        }
        implementation(class: 'events.service.HelloImpl') {
            resources(ref: 'impl.jars')
        }
        comment "Hello World Event Producer Example"

        configuration '''
            import net.jini.core.entry.Entry;
                import org.rioproject.entry.UIDescriptorFactory;
                events.service {
                    serviceUIs =
                        new Entry[]{
                            UIDescriptorFactory.getJComponentDesc(
                                       (String)$data,
                                       "events/lib/hello-event-ui.jar",
                                       "events.service.ui.HelloEventUI")};
            }'''

        maintain 1
    }

    service(name: 'Hello Event Consumer') {
        implementation(class: 'events.service.HelloEventConsumer') {
            resources(ref: 'impl.jars')
        }
        comment "Hello World Event Consumer Example"
        maintain 1
    }
}
