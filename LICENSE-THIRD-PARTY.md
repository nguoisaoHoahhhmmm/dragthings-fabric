This project includes code and assets copied and adapted from Visuality
(https://github.com/PinkGoosik/visuality, https://modrinth.com/mod/visuality),
used under the MIT License:

------------------------------------------------------------------------------

MIT License

Copyright (c) 2021 Alexey Belyaev

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

------------------------------------------------------------------------------

Files derived from Visuality:
- src/client/java/dragthings/client/particle/SparkleParticle.java
  (adapted from visuality.particle.SparkleParticle)
- src/client/java/dragthings/client/particle/BoneParticle.java
  (particle motion logic adapted from visuality.particle.SolidFallingParticle)
- src/main/resources/assets/dragthings/textures/particle/material/emerald.png
  (copied from Visuality without modification)
- src/main/resources/assets/dragthings/textures/particle/effect/charge_0.png - charge_3.png
  (copied from Visuality without modification)
- src/main/resources/assets/dragthings/textures/particle/effect/sparkle_0.png - sparkle_5.png
  (copied from Visuality without modification)
- src/main/resources/assets/dragthings/textures/particle/material/diamond.png
  (adapted and recolored from Visuality's sparkle.png)

Note: class/method names differ from the original because this project is
built against Mojang's official mappings, while Visuality is built against
Yarn mappings — the underlying logic is what was adapted, not the exact
source text.
