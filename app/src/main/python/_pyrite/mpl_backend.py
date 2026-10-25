"""matplotlib backend for Pyrite: plt.show() puts each figure in the console as an image.

The app selects it with MPLBACKEND=module://_pyrite.mpl_backend. Drawing is plain Agg,
so savefig() and everything else work as usual.
"""

from matplotlib._pylab_helpers import Gcf
from matplotlib.backend_bases import FigureManagerBase
from matplotlib.backends.backend_agg import FigureCanvasAgg

import _pyrite

# Phone screens are dense: a default 6.4 x 4.8 in figure becomes 960 x 720 px.
DPI = 150


class FigureManagerPyrite(FigureManagerBase):
    def show(self):
        """fig.show(), and plt.show() for each open figure: render it into the console."""
        path = _pyrite.new_image_path(".png")
        self.canvas.figure.savefig(path, format="png", dpi=DPI)
        _pyrite.show_image(path)

    @classmethod
    def pyplot_show(cls, *, block=None):
        try:
            for manager in Gcf.get_all_fig_managers():
                manager.show()
        finally:
            # Like a notebook: shown figures are closed, so the next plt.show() only
            # shows what was drawn since.
            Gcf.destroy_all()


class FigureCanvasPyrite(FigureCanvasAgg):
    manager_class = FigureManagerPyrite


FigureCanvas = FigureCanvasPyrite
FigureManager = FigureManagerPyrite
