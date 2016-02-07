# QHotkey
A global shortcut/hotkey for Desktop Qt-Applications.

The QHotkey is a class that can be used to create hotkeys/global shortcuts, aka shortcuts that work everywhere, independent of the application state. This means your application can be active, inactive, minimized or not visible at all and still receive the shortcuts.

## Status
**Still in development!!!** - But almost done. There are only some small details, like debug output, and documentation left. It's ready to be used.

## Features
 - Works on Windows, Mac and X11
 - Easy to use, can use `QKeySequence` for easy shortcut input
 - Supports almost all common keys (Depends on OS & Keyboard-Layout)
 - Allows direct input of Key/Modifier-Combinations
 - Supports multiple QHotkey-instances for the same shortcut (with optimisations)
 - Thread-Safe - Can be used on all threads (See section Thread safety)

## Usage
Just copy the `./QHotkey` folder into you application and add the line `include(./QHotkey/qhotkey.pri)` to your .pro-file. This way all files and required libraries will automatically be added. Use `#include <QHotkey>` to access the class.

### Example
The following example shows a simple application that will run without a window in the background until you press the key-combination <kbd>Ctrl</kbd>+<kbd>Alt</kbd>+<kbd>Q</kbd> (<kbd>⌘</kbd>+<kbd>⌥</kbd>+<kbd>Q</kbd> on Mac). This will quit the application. The debug output will tell if the hotkey was successfully registered and that it was pressed.
```cpp
#include <QHotkey>
#include <QApplication>
#include <QDebug>

int main(int argc, char *argv[])
{
    QApplication a(argc, argv);

    QHotkey hotkey(QKeySequence("ctrl+alt+Q"), true);//The hotkey will be automatically registered
    qDebug() << "Is Registered: " << hotkey.isRegistered();

    QObject::connect(&hotkey, &QHotkey::activated, qApp, [&](){
        qDebug() << "Hotkey Activated - the application will quit now";
        qApp->quit();
    });

    return a.exec();
}
```

**Note:** You need the .pri include for this to work.

### Testing
By running the example in `./HotkeyTest` you can test out the QHotkey class. There are 3 sections:
 - **Playground:** You can enter some sequences here and try it out with different key combinations.
 - **Testings:** A list of selected hotkeys. Activate it and try out which ones work for you (*Hint:* Depending on OS and keyboard layout, it's very possible that a few don't work).
 - **Threading:** Activate the checkbox to move 2 Hotkeys of the playground to seperate threads. It should work without a difference.

## Thread saftey
The QHotkey class itself is reentrant - wich means you can create as many instances as required on any thread. This allows you to use the QHotkey on all threads. **But** you should never use the QHotkey instance on a thread that is different from the one the instance belongs to! Internally the system uses a singleton instance that handles the hotkey events and distributes them to the QHotkey instances. This internal class is completley threadsafe.

However, this singleton instance only runs on the main thread. (One reason is that some of the OS-Functions are not thread safe). To make threaded hotkeys possible, the critical functions (registering/unregistering hotkeys and keytranslation) are all run on the mainthread too. The QHotkey instances on other threads use `QMetaObject::invokeMethod` with a `Qt::BlockingQueuedConnection`.

For you this means: QHotkey instances on other threads than the main thread may take a little longer to register/unregister/translate hotkeys, because they have to wait for the main thread to do this for them.

## Technical
### Requirements
 - I built it with Qt 5.5.1, but may work with earlier versions, too
 - At least the QtGui-Module (a QGuiApplication). Hotkeys on console based applications are not supported. (By the operating systems)
 - C++11

### Known Limitations
 - Qt::Key makes no difference between normal numbers and the Numpad numbers. Most keyboards however require this. Thus, you can't register shortcuts for the numpad.
 - Supports not all keys, but most of the common ones. There are differences between platforms and it depends on the Keyboard-Layout. "Delete", for example, works on windows and mac, but not on X11 (At least on my test machines). I tried to use OS-Functions where possible, but since the Qt::Key values need to be converted into native keys, there are some limitations.
 - The registered keys will be "taken" by QHotkey. This means after a hotkey was cosumend by your application, it will not be sent to the active application. This is done this way by the operating systems and cannot be changed.
