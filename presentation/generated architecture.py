import graphviz

def generate_architecture():
    # Create a new directed graph
    dot = graphviz.Digraph('SeeStar_Voice_2_Architecture', comment='SeeStar Voice 2')
    dot.attr(rankdir='TB', size='10,10', fontname='Arial')
    dot.attr('node', fontname='Arial', fontsize='10')

    # UI Layer
    with dot.subgraph(name='cluster_ui') as c:
        c.attr(label='UI Layer (Jetpack Compose)', color='royalblue', style='filled', fillcolor='aliceblue')
        c.node('MS', 'MainScreen.kt\n(Dashboard UI)')
        c.node('VM', 'MainViewModel.kt\n(State & Logic Coordinator)', shape='box', style='bold')
        c.node('SD', 'SettingsDialog\n(User Preferences)')
        c.edge('MS', 'VM', dir='both')
        c.edge('SD', 'VM')

    # Intelligence Layer
    with dot.subgraph(name='cluster_logic') as c:
        c.attr(label='Intelligence & Logic', color='forestgreen', style='filled', fillcolor='honeydew')
        c.node('IP', 'IntentProcessor.kt\n(Command Parser)')
        c.node('LM', 'LiteRT-LM Engine\n(Qwen 2.5 Model)', shape='hexagon', fillcolor='lightyellow', style='filled')
        c.node('AU', 'AstroUtils.kt\n(Coordinate Math)')
        c.node('TC', 'TelescopeController.kt\n(HTTP Client)')
        c.edge('VM', 'IP')
        c.edge('IP', 'LM')
        c.edge('VM', 'AU')
        c.edge('VM', 'TC')

    # System Services Layer
    with dot.subgraph(name='cluster_services') as c:
        c.attr(label='System Services', color='darkorange', style='filled', fillcolor='seashell')
        c.node('VR', 'VoiceRecognizer\n(Android Speech)')
        c.node('TM', 'TtsManager\n(Android TTS)')
        c.edge('VM', 'VR', dir='both', label='Wake Word')
        c.edge('VM', 'TM', label='Feedback')

    # Data Layer
    with dot.subgraph(name='cluster_data') as c:
        c.attr(label='Data & Persistence', color='purple', style='filled', fillcolor='lavender')
        c.node('SM', 'SettingsManager\n(DataStore Preferences)')
        c.node('DB', 'AppDatabase\n(Room / SQLite)', shape='cylinder')
        c.node('CSV', 'NGC Catalog\n(Asset CSV)')
        c.edge('VM', 'SM', dir='both')
        c.edge('VM', 'DB', dir='both')
        c.edge('DB', 'CSV', style='dashed')

    # External Hardware
    dot.node('SS', 'SeeStar Telescope\n(External Hardware)', shape='doublecircle', color='red')
    dot.edge('TC', 'SS', label='Alpaca API\nPort 32323', color='red', style='bold')

    # Render the diagram
    dot.render('seestar_architecture', format='png', cleanup=True)
    print("Architecture diagram generated as 'seestar_architecture.png'")

if __name__ == "__main__":
    generate_architecture()