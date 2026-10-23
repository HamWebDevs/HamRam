package dev.hamweb.stayopen.privileged;

// Finite operations only. There is deliberately no arbitrary command or force-stop API.
interface IRamService {
    String scan(in String[] allowed, in String[] protectedApps) = 1;
    String clear(in String[] allowed, in String[] protectedApps) = 2;
    void destroy() = 16777114;
}
