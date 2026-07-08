import io.ic.starter.app.StarterSetup;
import io.ic.starter.starterenv.Environment;
import io.ic.starter.websupport.App;

void main() {
    var env = Environment.fromEnv();
    new App(new StarterSetup(env)).startAndBlock(env.port());
}
