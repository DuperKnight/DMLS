package com.duperknight.client.rulebook;

final class RulebookFixtures {
    private RulebookFixtures() {
    }

    static String document() {
        return """
                <html><head><style>
                .red{color:#ff0000}.bold{font-weight:700}.italic{font-style:italic}
                .purple{color:#bf00ff;background-color:#1e2124}
                .alpha>li:before{content:"" counter(item,lower-latin) ". "}
                </style></head><body>
                <p class="title">⚒️ MC &amp; Discord Rules</p>
                <p class="title">📽️ RP &amp; Extra Systems</p>
                <h2>18. Ban Evasion, Weaponization, and Retaliation</h2>
                <p>Read the <a href="https://example.com/rules">source</a>
                <a href="javascript:alert(1)">unsafe</a><script>not searchable</script></p>
                <table><tr><th>Color Code</th><th>Ban length</th></tr><tr><td>Severe</td><td>Permanent</td></tr></table>
                <table><tr><td><p><span class="red bold">18.7</span></p></td><td>
                <p><span class="bold">Ban retaliation harassment:</span> Targeting reporters is not allowed.</p>
                <ol class="alpha"><li>First condition<ul><li>Nested note</li></ul></li><li><span class="purple">Grievous</span> condition</li></ol>
                <p><span class="italic">1 month ban, permanent ban</span></p>
                <p><img alt="pixel" style="width:100px;height:50px" src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="></p>
                </td></tr></table>
                <p></p>
                <h2>S5. Staff Abuse</h2>
                <table><tr><td style="width:45pt"><span class="bold">S5.1</span></td><td style="width:436.5pt">
                <p>Staff must not misuse permissions.</p></td></tr>
                <tr><td style="width:45pt"><span class="bold">Notes:</span></td><td style="width:436.5pt">
                <p>Remember the context.</p><ol class="alpha lst-kix-notes-1"><li>First note</li><li>Second note 🏅 🥈 🥉
                <img alt="table pixel" style="width:80px;height:40px" src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="></li><li>Third note</li></ol>
                </td></tr></table>
                </body></html>
                """;
    }
}
